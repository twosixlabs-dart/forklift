package com.twosixlabs.dart.forklift.helpers

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.scala.DefaultScalaModule

import scala.util.Try

object Mapper {
    private val m : ObjectMapper = {
        val mapper = new ObjectMapper()
        mapper.registerModule( DefaultScalaModule )
        mapper.registerModule( new JavaTimeModule )
        mapper
    }

    def unmarshal[ A ]( json : String, valueType : Class[ A ] ) : Try[ A ] = {
        Try {
            m.readValue( json, valueType )
        }
    }

    def marshal( dto : Any ) : Try[ String ] = {
        Try {
            m.writeValueAsString( dto )
        }
    }

}
