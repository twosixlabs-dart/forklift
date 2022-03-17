package com.twosixlabs.dart.forklift.api.models

import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class FileMetadataTest extends AnyFlatSpecLike with Matchers {

    behavior of "FileMetadata.apply"

    it should "generate metadata from DocumentMetadata using filename if sourceUri is empty" in {
        val docMetadata = DocumentMetadata( None, None, None, None, None, None )

        val fileMetadata = FileMetadata( docMetadata, "test-filename" )

        fileMetadata.sourceUri shouldBe "test-filename"
    }

    it should "generate metadata from DocumentMetadata using sourceUri and not filename is sourceUri is defined" in {
        val docMetadata = DocumentMetadata( None, Some( "test-source-uri" ), None, None, None, None )

        val fileMetadata = FileMetadata( docMetadata, "test-filename" )

        fileMetadata.sourceUri shouldBe "test-source-uri"
    }

}
