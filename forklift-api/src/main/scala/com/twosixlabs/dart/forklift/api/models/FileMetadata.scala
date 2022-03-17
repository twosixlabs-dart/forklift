package com.twosixlabs.dart.forklift.api.models

import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.annotation.{JsonInclude, JsonProperty}
import sttp.tapir.encodedName

import scala.beans.BeanProperty

object FileMetadata {
    def apply( documentMetadata : DocumentMetadata, fileName : String ) : FileMetadata = {
        FileMetadata( documentMetadata.tenants,
                      documentMetadata.sourceUri.getOrElse( fileName ),
                      documentMetadata.genre,
                      documentMetadata.ingestionSystem,
                      documentMetadata.labels,
                      documentMetadata.reAnnotate)
    }

    def apply( fileName : String ) : FileMetadata = {
        FileMetadata( None, fileName, None, None, None, None )
    }
}


@JsonInclude( Include.NON_EMPTY )
case class FileMetadata( @BeanProperty @JsonProperty( "tenants" ) @encodedName( "tenants" ) tenants : Option[ Set[ String ] ],
                         @BeanProperty @JsonProperty( "source_uri" ) @encodedName( "source_uri" ) sourceUri : String ,
                         @BeanProperty @JsonProperty( "genre" ) @encodedName( "genre" ) genre : Option[ String ],
                         @BeanProperty @JsonProperty( "ingestion_system" ) @encodedName( "ingestion_system" ) ingestionSystem : Option[ String ],
                         @BeanProperty @JsonProperty( "labels" ) @encodedName( "labels" ) labels : Option[ List[ String ] ],
                         @BeanProperty @JsonProperty( "reannotate" ) @encodedName( "reannotate" ) reAnnotate : Option[ Boolean ])

