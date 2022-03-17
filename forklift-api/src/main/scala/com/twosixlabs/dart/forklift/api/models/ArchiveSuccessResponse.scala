package com.twosixlabs.dart.forklift.api.models

import com.fasterxml.jackson.annotation.JsonProperty
import sttp.tapir.encodedName

import scala.beans.BeanProperty

case class ArchiveSuccessResponse(
    @BeanProperty @JsonProperty( "documents" ) @encodedName( "documents" ) documents: Seq[ SuccessResponse ],
    @BeanProperty @JsonProperty( "num_docs_failed" ) @encodedName( "num_docs_failed" ) numDocumentsFailed: Int
) {}


case class SuccessResponse(
    @BeanProperty @JsonProperty( "document_id" ) @encodedName( "document_id" ) documentId : String,
    @BeanProperty @JsonProperty( "filename" ) @encodedName( "filename" ) filename : String,
)
