package com.twosixlabs.dart.forklift.api.models

object ResponseErrorCode extends Enumeration {
    type ResponseErrorCode = Value

    val UnableToSaveDocument = Value( "Unable to save document" )
    val UnableToSaveMetaData = Value( "Unable to save metadata" )
    val ServerError = Value( "Server Error" )
    val MissingFile = Value( "File was not provided" )
    val NotZipFile = Value( "File extension is not .zip" )
    val UnableToExtractFromZip = Value( "Unable to extract documents from zip" )
    val MissingMetadata = Value( "Metadata was not provided" )
    val MalformedMetadata = Value( "Metadata was not correctly provided" )
    val MissingTenant = Value( "Tenant was not provided" )

}
