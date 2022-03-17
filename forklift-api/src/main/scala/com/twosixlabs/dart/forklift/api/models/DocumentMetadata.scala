package com.twosixlabs.dart.forklift.api.models

import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.annotation.{JsonIgnore, JsonIgnoreProperties, JsonInclude, JsonProperty}
import sttp.tapir.{description, encodedName}

import scala.beans.BeanProperty

@JsonInclude( Include.NON_EMPTY )
@JsonIgnoreProperties( ignoreUnknown = true )
case class DocumentMetadata( @BeanProperty @JsonProperty( "tenants" ) @encodedName( "tenants" ) @description( "Id of tenant to upload file to; if empty, assumed target will be the global corpus" ) tenants : Option[ Set [ String ] ],
                             @BeanProperty @JsonProperty( "source_uri" ) @encodedName( "source_uri" ) @description( "Original raw document filename" ) sourceUri : Option[ String ],
                             @BeanProperty @JsonProperty( "genre" ) @encodedName( "genre" ) genre : Option[ String ],
                             @BeanProperty @JsonProperty( "ingestion_system" ) @encodedName( "ingestion_system" ) ingestionSystem : Option[ String ],
                             @BeanProperty @JsonProperty( "labels" ) @encodedName( "labels" ) labels : Option[ List[ String ] ],
                             @BeanProperty @JsonProperty( "reannotate" ) @encodedName( "reannotate" ) reAnnotate : Option[ Boolean ]) {

    @JsonIgnore
    def isEmpty( ) : Boolean = {
        tenants.isEmpty &&
        sourceUri.isEmpty &&
        genre.isEmpty &&
        ingestionSystem.isEmpty &&
        labels.isEmpty &&
        reAnnotate.isEmpty
    }
}
