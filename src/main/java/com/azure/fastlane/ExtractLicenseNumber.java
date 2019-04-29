package com.azure.fastlane;

import com.microsoft.azure.cognitiveservices.vision.customvision.prediction.models.Prediction;
import com.microsoft.azure.functions.ExecutionContext;
import org.apache.commons.io.FileUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;

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


    public static void main(String[] args) {
        String blobName = "test1.jpg";
    
        AzureUtils utils = new AzureUtils(blobName);
        try {
            downloadFile(blobName);
            Prediction prediction = utils.makePredictionRequest(rawImage);
            cropImageAfterPrediction(rawImage, prediction);
            utils.uploadFileToBlobStorage("kvish6-cropped", blobName, croppedImage);
            licneseNumber = utils.makeOCRRequest();
            utils.writeEntryToDBAndMoveToProcessedContainer(licneseNumber);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            writeToErrorQueueAndMoveBlob(utils);
        }
    }


    public static void writeToErrorQueueAndMoveBlob(AzureUtils utils) {
        try {
            utils.writeToErrorQueue();
            utils.moveBlobToNewContainer(pendingValidationBlobContainerURL + utils.getBlobName(), "kvish6-manual-validation");
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.out.println("Failed writing error message for "+ utils.getBlobName());
        }
    }


    private static void cropImageAfterPrediction(File fileToCrop, Prediction prediction) throws IOException {
            BufferedImage bufferedImage = ImageIO.read(fileToCrop);
            System.out.println("Original Image Dimension: " + bufferedImage.getWidth() + "x" + bufferedImage.getHeight());
            BufferedImage croppedImage = bufferedImage.getSubimage((int) (prediction.boundingBox().left() * 1000.0f) - 50, (int) (prediction.boundingBox().top() * 1000.0f) - 35, (int) (prediction.boundingBox().width() * 1000.0f), (int) (prediction.boundingBox().height() * 1000.0f));
            System.out.println("Cropped Image Dimension: " + croppedImage.getWidth() + "x" + croppedImage.getHeight());
            ExtractLicenseNumber.croppedImage = new File("cropped.jpg");
            ImageIO.write(croppedImage, "jpg", ExtractLicenseNumber.croppedImage);
            System.out.println("Image cropped successfully");
    }

    private static void downloadFile(String blobName) throws Exception {
        rawImage = new File("raw.jpg");
        FileUtils.copyURLToFile(new URL(pendingValidationBlobContainerURL + blobName), rawImage);

    }


}
