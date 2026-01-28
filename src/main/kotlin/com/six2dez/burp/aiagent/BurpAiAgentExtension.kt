package com.six2dez.burp.aiagent

import burp.api.montoya.BurpExtension
import burp.api.montoya.MontoyaApi

class BurpAiAgentExtension : BurpExtension {
    override fun initialize(api: MontoyaApi) {
        App.initialize(api)
        api.extension().registerUnloadingHandler {
            App.shutdown()
        }
    }
}
