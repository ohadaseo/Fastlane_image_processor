package com.azure.fastlane;

import com.google.gson.Gson;
import com.microsoft.azure.cognitiveservices.vision.customvision.prediction.CustomVisionPredictionManager;
import com.microsoft.azure.cognitiveservices.vision.customvision.prediction.PredictionEndpoint;
import com.microsoft.azure.cognitiveservices.vision.customvision.prediction.models.ImagePrediction;
import com.microsoft.azure.cognitiveservices.vision.customvision.prediction.models.Prediction;
import com.microsoft.azure.functions.ExecutionContext;
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
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.bson.Document;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.UUID;

public class AzureUtils {

    private static final String subscriptionKey = "3733733beb404b4cbebbfc87ba1f19bd";
    private static final String visionUriBase = "https://southcentralus.api.cognitive.microsoft.com/vision/v2.0/ocr";
    private static final String storageAccountConnectionString = "DefaultEndpointsProtocol=https;AccountName=fastlaneblobs;AccountKey=CeGaoYc/cwpcf44WQpgEP61ERm8cQGwe364M1zaPimeUEOGNVCXZktPmYUBfq9zl5YIFBQz0bB6/Sz/cD2OvJA==;EndpointSuffix=core.windows.net";
    private static final String cosmosDBConnectionString = "mongodb://fastlane-license-numbers:erUEUrROnDqbPb4Lhaxs84eDMGEhLYiKFZ92EEbKHEJBcwugJiFZaTh8XZPKRZZyAUkAz3NKQIwsFMbsubKDOg==@fastlane-license-numbers.documents.azure.com:10255/?ssl=true&replicaSet=globaldb";
    private static String blobName;
    private static String blobURL;
    private static String uploadedCroppedFileURI;
    private static String blobContainersBaseURL = "https://fastlaneblobs.blob.core.windows.net/";
    private static String pendingValidationBlobContainerURL = blobContainersBaseURL + "kvish6-pending-validation/";
    private static String manualvalidationBlobContainerURL = blobContainersBaseURL + "kvish6-manual-validation/";
    private static String croppedImagesBlobContainerURL = blobContainersBaseURL + "kvish6-cropped/";
    private static String processedImagesBlobContainerURL = blobContainersBaseURL + "kvish6-processed/";


    public AzureUtils(String blobName) {
        this.blobName = blobName;
        this.blobURL = pendingValidationBlobContainerURL + blobName;
    }


    public void writeEntryToDBAndMoveToProcessedContainer(String licneseNumber) throws Exception {
        moveBlobToNewContainer(pendingValidationBlobContainerURL + blobName, "kvish6-processed");

        BillingRecord record = new BillingRecord(processedImagesBlobContainerURL + blobName, licneseNumber);
        Gson gson = new Gson();
        final Document doc = Document.parse(gson.toJson(record));

        MongoClient mongoClient = new MongoClient(new MongoClientURI(cosmosDBConnectionString));
        MongoDatabase db = mongoClient.getDatabase("kvish6");
        MongoCollection<Document> numbersForBilling = db.getCollection("numbers_for_billing");
        numbersForBilling.insertOne(doc);
        System.out.println("License number found, Added a new entry for "+blobName+", Moving the image to proccesed container");
    }

    public void uploadFileToBlobStorage(String containerName, String blobName, File fileToUpload) throws Exception {
        CloudStorageAccount storageAccount;
        CloudBlobClient blobClient = null;
        CloudBlobContainer container = null;

        storageAccount = CloudStorageAccount.parse(storageAccountConnectionString);
        blobClient = storageAccount.createCloudBlobClient();
        container = blobClient.getContainerReference(containerName);

        container.createIfNotExists(BlobContainerPublicAccessType.CONTAINER, new BlobRequestOptions(), new OperationContext());

        //Getting a blob reference
        CloudBlockBlob blob = container.getBlockBlobReference(blobName);
        uploadedCroppedFileURI = blob.getUri().toString();
        //Creating blob and uploading file to it
        System.out.println("Uploading the cropped file ");

        blob.uploadFromFile(fileToUpload.getAbsolutePath());
    }

    public static Prediction makePredictionRequest(File rawImage) throws Exception {
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
            throw new Exception("Prediction failed, Moving to error handling");
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

    public String makeOCRRequest() throws Exception {
        String licenseNumber = null;
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
                    new StringEntity("{\"url\":\"" + uploadedCroppedFileURI + "\"}");
            request.setEntity(requestEntity);

            HttpResponse response = httpClient.execute(request);
            HttpEntity entity = response.getEntity();

            if (entity != null) {
                Gson gson = new Gson();
                String jsonString = EntityUtils.toString(entity);

                OCRResponse ocrResponse = gson.fromJson(jsonString, OCRResponse.class);
                if (ocrResponse.getRegions().size() > 0) {
                    if (ocrResponse.getRegions().get(0).getLines().get(0).getWords().size() > 1) {
                        licenseNumber = ocrResponse.getRegions().get(0).getLines().get(0).getWords().get(1).getText();
                        System.out.println("Found license number: "+licenseNumber);
                        System.out.println("More than 1 result returned from OCR, Moving to manual validation");
                        //throw new Exception("OCR Reponse error, Moving to manual validation");
                    }
                } else {
                    System.out.println("No number extracted, Moving to manual validation");
                    throw new Exception("OCR Reponse error, Moving to manual validation");
                }
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            throw new Exception("No license number found, Moving to error handling");
        }

        return licenseNumber;
    }

    public void writeToErrorQueue() throws Exception {
        CloudStorageAccount storageAccount = CloudStorageAccount.parse(storageAccountConnectionString);
        CloudQueueClient queueClient = storageAccount.createCloudQueueClient();
        CloudQueue queue = queueClient.getQueueReference("kvish6-validation-error");
        queue.createIfNotExists();
        CloudQueueMessage message = new CloudQueueMessage(blobName);
        queue.addMessage(message);
        System.out.println("Added a new queue error message for  " + blobName);
    }

    public void moveBlobToNewContainer(String sourceBlob, String destinationContainer) throws Exception {
        File blobForUpload = new File(blobName);
        FileUtils.copyURLToFile(new URL(sourceBlob), blobForUpload);
        uploadFileToBlobStorage(destinationContainer, blobName, blobForUpload);
        System.out.println("Uploaded file " + blobName + " to "+ destinationContainer);
    }

    public static String getBlobName() {
        return blobName;
    }

    public static void setBlobName(String blobName) {
        AzureUtils.blobName = blobName;
    }

    public static String getBlobURL() {
        return blobURL;
    }

    public static void setBlobURL(String blobURL) {
        AzureUtils.blobURL = blobURL;
    }

    public static String getBlobContainersBaseURL() {
        return blobContainersBaseURL;
    }

    public static void setBlobContainersBaseURL(String blobContainersBaseURL) {
        AzureUtils.blobContainersBaseURL = blobContainersBaseURL;
    }
}
