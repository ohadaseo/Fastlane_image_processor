package com.azure.fastlane;

import com.microsoft.azure.cognitiveservices.vision.customvision.prediction.models.Prediction;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.BlobTrigger;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.StorageAccount;
import org.apache.commons.io.FileUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.logging.Logger;

import com.microsoft.azure.functions.ExecutionContext;

/**
 * Azure Functions with Azure Blob trigger.
 */
public class ExtractLicenseNumber {

    private static File rawImage;
    private static File croppedImage;

    private static ExecutionContext contextLogging;

    private static String licneseNumber;
    private static String blobContainersBaseURL = "https://fastlaneblobs.blob.core.windows.net/";
    private static String pendingValidationBlobContainerURL = blobContainersBaseURL + "kvish6-pending-validation/";
    private static String manualvalidationBlobContainerURL = blobContainersBaseURL + "kvish6-manual-validation/";
    private static String croppedImagesBlobContainerURL = blobContainersBaseURL + "kvish6-cropped/";
    private static String processedImagesBlobContainerURL = blobContainersBaseURL + "kvish6-processed/";


    /**
     * This function will be invoked when a new or updated blob is detected at the specified path. The blob contents are provided as input to this function.
     */
    @FunctionName("ExtractLicenseNumber")
    @StorageAccount("fastlaneblobs_storage")
    public void run(
            @BlobTrigger(name = "content", path = "kvish6-pending-validation/{name}", dataType = "binary", connection = "fastlaneblobs_storage") byte[] content,
            @BindingName("name") String blobName,
            final ExecutionContext context
    ) {
    contextLogging = context;
   /* public static void main(String[] args) {
        final ExecutionContext contextLogging = new ExecutionContext() {
            @Override
            public Logger getLogger() {
                return null;
            }

            @Override
            public String getInvocationId() {
                return null;
            }

            @Override
            public String getFunctionName() {
                return null;
            }
        };*/
        //String blobName = "test1.jpg";
        contextLogging.getLogger().info("Java Blob trigger function processed a blob. Name: " + blobName + "\n  Size: " + content.length + " Bytes");
        AzureUtils utils = new AzureUtils(blobName, contextLogging);
        try {
            downloadFile(blobName);
            Prediction predicition = utils.makePredictionRequest(rawImage);
            cropImageAfterPrediction(rawImage, predicition);
            utils.uploadFileToBlobStorage("kvish6-cropped", blobName, croppedImage);
            licneseNumber = utils.makeOCRRequest();
            utils.writeEntryToDBAndMoveToProcessedContainer(licneseNumber);
        } catch (Exception e) {
            contextLogging.getLogger().info(e.getMessage());
            writeToErrorQueueAndMoveBlob(utils);
        }
    }


    public static void writeToErrorQueueAndMoveBlob(AzureUtils utils) {
        try {
            utils.writeToErrorQueue();
            utils.moveBlobToNewContainer(pendingValidationBlobContainerURL + utils.getBlobName(), manualvalidationBlobContainerURL);
        } catch (Exception e) {
            contextLogging.getLogger().info("Failed writing error message for "+ utils.getBlobName());
        }
    }


    private static boolean cropImageAfterPrediction(File fileToCrop, Prediction prediction) {
        try {
            BufferedImage bufferedImage = ImageIO.read(fileToCrop);
            contextLogging.getLogger().info("Original Image Dimension: " + bufferedImage.getWidth() + "x" + bufferedImage.getHeight());
            BufferedImage croppedImage = bufferedImage.getSubimage((int) (prediction.boundingBox().left() * 1000.0f) - 50, (int) (prediction.boundingBox().top() * 1000.0f) - 35, (int) (prediction.boundingBox().width() * 1000.0f), (int) (prediction.boundingBox().height() * 1000.0f));
            contextLogging.getLogger().info("Cropped Image Dimension: " + croppedImage.getWidth() + "x" + croppedImage.getHeight());
            ExtractLicenseNumber.croppedImage = new File("images/cropped.jpg");
            ImageIO.write(croppedImage, "jpg", ExtractLicenseNumber.croppedImage);
            contextLogging.getLogger().info("Image cropped successfully: ");
        } catch (IOException e) {
            contextLogging.getLogger().info(e.getMessage());
            return false;
        }

        return true;
    }

    private static void downloadFile(String blobName) throws Exception {
        rawImage = new File("images/raw.jpg");
        FileUtils.copyURLToFile(new URL(pendingValidationBlobContainerURL + blobName), rawImage);

    }


}
