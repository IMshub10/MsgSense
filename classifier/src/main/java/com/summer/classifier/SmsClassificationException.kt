package com.summer.classifier

class SmsClassificationException(private val exceptionMessage: String) : RuntimeException() {
    override val message: String
        get() = "Some Error Occurred while classifying Sms $exceptionMessage"
}