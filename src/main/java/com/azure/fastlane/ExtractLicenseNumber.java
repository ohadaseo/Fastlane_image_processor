package com.azure.fastlane;


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
    }
}
