package com.rommansabbir.secugensdk

import SecuGen.FDxSDKPro.SGFDxTemplateFormat

sealed class FingerprintTemplateType {
    object TemplateISO : FingerprintTemplateType()
    object TemplateANSI : FingerprintTemplateType()

    object Parser {
        fun parse(type: FingerprintTemplateType): Short {
            return when (type) {
                TemplateISO -> SGFDxTemplateFormat.TEMPLATE_FORMAT_ISO19794
                TemplateANSI -> SGFDxTemplateFormat.TEMPLATE_FORMAT_ANSI378
            }
        }
    }
}


