package com.azure.fastlane;
import com.google.gson.Gson;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.microsoft.azure.cognitiveservices.vision.customvision.prediction.CustomVisionPredictionManager;
import com.microsoft.azure.cognitiveservices.vision.customvision.prediction.PredictionEndpoint;
import com.microsoft.azure.cognitiveservices.vision.customvision.prediction.models.ImagePrediction;
import com.microsoft.azure.cognitiveservices.vision.customvision.prediction.models.Prediction;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.OperationContext;
import com.microsoft.azure.storage.blob.*;
import com.microsoft.azure.storage.queue.CloudQueue;
import com.microsoft.azure.storage.queue.CloudQueueClient;
import com.microsoft.azure.storage.queue.CloudQueueMessage;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.bson.Document;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.UUID;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.BlobTrigger;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.StorageAccount;

import java.security.cert.Extension;

/**
 * Azure Functions with Azure Blob trigger.
 */
public class ExtractLicenseNumber {


    private static final String subscriptionKey = "3733733beb404b4cbebbfc87ba1f19bd";

    private static final String visionUriBase = "https://southcentralus.api.cognitive.microsoft.com/vision/v2.0/ocr";

    private static final String imageToAnalyze = "https://fastlaneblobs.blob.core.windows.net/kvish6-pending-validation/test1.jpg";

    private static final String storageConnectionString = "DefaultEndpointsProtocol=https;AccountName=fastlaneblobs;AccountKey=CeGaoYc/cwpcf44WQpgEP61ERm8cQGwe364M1zaPimeUEOGNVCXZktPmYUBfq9zl5YIFBQz0bB6/Sz/cD2OvJA==;EndpointSuffix=core.windows.net";

    private static String blobName;

    private static File rawImage;

    private static File croppedImagePath;

    private static String uploadedFileURL;

    private static boolean moveToErrorHandling;

    private static  String licneseNumber;

    /**
     * This function will be invoked when a new or updated blob is detected at the specified path. The blob contents are provided as input to this function.
     */
    @FunctionName("ExtractLicenseNumber")
    @StorageAccount("fastlaneblobs_storage")
    public void run(
        @BlobTrigger(name = "content", path = "kvish6-pending-validation/{name}", dataType = "binary", connection = "fastlaneblobs_storage") byte[] content,
        @BindingName("name") String name,
        final ExecutionContext context
    ) {
        context.getLogger().info("Java Blob trigger function processed a blob. Name: " + name + "\n  Size: " + content.length + " Bytes");
        try {
            blobName = FilenameUtils.getName(new URL(imageToAnalyze).getPath());
            moveToErrorHandling = false;
            rawImage = new File("/Users/ohada/dev/comazurefastlane/raw.jpg");
            croppedImagePath = new File("/Users/ohada/dev/comazurefastlane/test1-after-crop.jpg");
            downloadFile();
            Prediction predicition = makePredictionRequest();
            cropImageAfterPrediction(rawImage, predicition);
            uploadCropedImageToBlobStorage();
            if (moveToErrorHandling) {
                writeToErrorQueueAndMoveBlob();
            } else {
                makeOCRRequest();
            }
            if (moveToErrorHandling) {
                writeToErrorQueueAndMoveBlob();
            } else {
                writeEntryToDB();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void writeEntryToDB() {
        MongoClient mongoClient = new MongoClient(new MongoClientURI("mongodb://fastlane-license-numbers:erUEUrROnDqbPb4Lhaxs84eDMGEhLYiKFZ92EEbKHEJBcwugJiFZaTh8XZPKRZZyAUkAz3NKQIwsFMbsubKDOg==@fastlane-license-numbers.documents.azure.com:10255/?ssl=true&replicaSet=globaldb"));
        MongoDatabase db = mongoClient.getDatabase("kvish6");
        MongoCollection<Document> numbersForBilling = db.getCollection("numbers_for_billing");
        BillingRecord record = new BillingRecord(imageToAnalyze,licneseNumber);
        Gson gson = new Gson();
        final Document doc = Document.parse(gson.toJson(record));
        numbersForBilling.insertOne(doc);
    }

    private static void writeToErrorQueueAndMoveBlob() {

        try {
            CloudStorageAccount storageAccount = CloudStorageAccount.parse(storageConnectionString);

            CloudQueueClient queueClient = storageAccount.createCloudQueueClient();

            CloudQueue queue = queueClient.getQueueReference("kvish6-validation-error");

            queue.createIfNotExists();

            CloudQueueMessage message = new CloudQueueMessage(blobName);
            queue.addMessage(message);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private static void uploadCropedImageToBlobStorage() {
        CloudStorageAccount storageAccount;
        CloudBlobClient blobClient = null;
        CloudBlobContainer container = null;

        try {
            storageAccount = CloudStorageAccount.parse(storageConnectionString);
            blobClient = storageAccount.createCloudBlobClient();
            container = blobClient.getContainerReference("kvish6-cropped");

            container.createIfNotExists(BlobContainerPublicAccessType.CONTAINER, new BlobRequestOptions(), new OperationContext());

            //Getting a blob reference
            CloudBlockBlob blob = container.getBlockBlobReference(croppedImagePath.getName());
            uploadedFileURL = blob.getUri().toString();
            //Creating blob and uploading file to it
            System.out.println("Uploading the cropped file ");
            blob.uploadFromFile(croppedImagePath.getAbsolutePath());
        } catch (Exception e) {
            System.out.println(e.getMessage());
            moveToErrorHandling = true;
        }
    }

    private static void cropImageAfterPrediction(File fileToCrop, Prediction prediction) {
        try {
            BufferedImage bufferedImage = ImageIO.read(fileToCrop);
            System.out.println("Original Image Dimension: " + bufferedImage.getWidth() + "x" + bufferedImage.getHeight());

            BufferedImage croppedImage = bufferedImage.getSubimage((int) (prediction.boundingBox().left() * 1000.0f) - 50, (int) (prediction.boundingBox().top() * 1000.0f) - 35, (int) (prediction.boundingBox().width() * 1000.0f), (int) (prediction.boundingBox().height() * 1000.0f));

            System.out.println("Cropped Image Dimension: " + croppedImage.getWidth() + "x" + croppedImage.getHeight());

            ImageIO.write(croppedImage, "jpg", croppedImagePath);

            System.out.println("Image cropped successfully: " + croppedImagePath.getPath());

        } catch (IOException e) {
            System.out.println(e.getMessage());
            moveToErrorHandling = true;
        }

    }

    private static void downloadFile() {
        try {
            FileUtils.copyURLToFile(new URL(imageToAnalyze), rawImage);
        } catch (IOException e) {
            System.out.println(e.getMessage());
            moveToErrorHandling = true;
        }

    }

    private static Prediction makePredictionRequest() {
        final String predictionApiKey = "75e433a9916d46139fa9be17285c22ff";
        final UUID projectID = UUID.fromString("067b567e-6b0c-4aa4-86b7-a770dae9e92c");
        final UUID iterationID = UUID.fromString("a1b74ff3-3e98-4f4b-826f-3aadfd02057b");

        PredictionEndpoint predictClient = CustomVisionPredictionManager.authenticate(predictionApiKey);


        byte[] bytesArray = new byte[(int) rawImage.length()];

        try {

            //init array with file length

            FileInputStream fis = new FileInputStream(rawImage);
            fis.read(bytesArray); //read file into bytes[]
            fis.close();
        } catch (IOException e) {
            System.out.println(e.getMessage());
            moveToErrorHandling = true;
        }

        byte[] testImage = bytesArray;

        ImagePrediction results = predictClient.predictions().predictImage()
                .withProjectId(projectID)
                .withImageData(testImage)
                .withIterationId(iterationID)
                .execute();

        Prediction highestScorePrediction = null;
        Prediction currentPrediction = new Prediction();

        for (Prediction prediction : results.predictions()) {

            if (prediction.tagName().equals("Registration Number")) {
                currentPrediction = prediction;

                if (highestScorePrediction != null) {

                    if (currentPrediction.probability() * 100.0f >= highestScorePrediction.probability() * 100.0f) {
                        highestScorePrediction = currentPrediction;
                    }

                } else {
                    highestScorePrediction = currentPrediction;
                }
            }
        }
        System.out.println(String.format("\t%s: %.0f%% at: %.0f, %.0f, %.0f, %.0f",
                highestScorePrediction.tagName(),
                highestScorePrediction.probability() * 100.0f,
                highestScorePrediction.boundingBox().left() * 1000,
                highestScorePrediction.boundingBox().top() * 1000,
                highestScorePrediction.boundingBox().width() * 1000,
                highestScorePrediction.boundingBox().height() * 1000
        ));

        return highestScorePrediction;
    }

    private static void makeOCRRequest() {
        CloseableHttpClient httpClient = HttpClientBuilder.create().build();

        try {
            URIBuilder uriBuilder = new URIBuilder(visionUriBase);

            uriBuilder.setParameter("language", "unk");
            uriBuilder.setParameter("detectOrientation", "true");

            URI uri = uriBuilder.build();
            HttpPost request = new HttpPost(uri);

            request.setHeader("Content-Type", "application/json");
            request.setHeader("Ocp-Apim-Subscription-Key", subscriptionKey);

            StringEntity requestEntity =
                    new StringEntity("{\"url\":\"" + uploadedFileURL + "\"}");
            request.setEntity(requestEntity);

            HttpResponse response = httpClient.execute(request);
            HttpEntity entity = response.getEntity();

            if (entity != null) {
                Gson gson = new Gson();
                String jsonString = EntityUtils.toString(entity);

                OCRResponse ocrResponse = gson.fromJson(jsonString, OCRResponse.class);
                if(ocrResponse.getRegions().size() > 0){
                    if(ocrResponse.getRegions().get(0).getLines().get(0).getWords().size() > 1){
                        licneseNumber = ocrResponse.getRegions().get(0).getLines().get(0).getWords().get(1).getText();

                        //moveToErrorHandling = true;
                        //System.out.println("More than 1 result returned from OCR, Moving to manual validation");
                    }
                }
                else {
                    System.out.println("No number extracted, Moving to manual validation");
                    moveToErrorHandling = true;
                }
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            moveToErrorHandling = true;
        }
    }

}
