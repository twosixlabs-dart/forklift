package com.twosixlabs.dart.forklift.procurement

import scala.util.Try

trait ProcurementRepository {

    def doSave( filename : String, fileContent : Array[ Byte ] ) : Try[ String ]

}
