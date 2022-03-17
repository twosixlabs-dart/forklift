package com.twosixlabs.dart.forklift.procurement.repositories

import com.twosixlabs.dart.aws.S3Bucket
import com.twosixlabs.dart.forklift.procurement.ProcurementRepository
import com.twosixlabs.dart.utils.RetryHelper

import scala.util.Try

class S3ProcurementRepository( s3Bucket : S3Bucket ) extends ProcurementRepository {

    private final val NUMBER_OF_RETRIES = 3

    override def doSave( fileName : String,
                         fileContent : Array[ Byte ] ) : Try[ String ] = {

        RetryHelper.retry( NUMBER_OF_RETRIES )( s3Bucket.create( fileName, fileContent ) )

    }
}
