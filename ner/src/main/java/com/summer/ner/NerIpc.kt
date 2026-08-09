package com.summer.ner

object NerIpc {
    const val MSG_EXTRACT = 1
    const val MSG_SUCCESS = 2
    const val MSG_FAILURE = 3

    const val KEY_REQUEST_ID = "request_id"
    const val KEY_SMS_ID = "sms_id"
    const val KEY_SENDER = "sender"
    const val KEY_BODY = "body"
    const val KEY_MODEL_ID = "model_id"
    const val KEY_MODEL_SHA256 = "model_sha256"
    const val KEY_TOKENIZER_SHA256 = "tokenizer_sha256"
    const val KEY_PREPROCESSING_VERSION = "preprocessing_version"
    const val KEY_PIPELINE_FINGERPRINT = "pipeline_fingerprint"
    const val KEY_LABEL_SCHEMA_SHA256 = "label_schema_sha256"
    const val KEY_DECODER_VERSION = "decoder_version"
    const val KEY_NORMALIZER_VERSION = "normalizer_version"
    const val KEY_TOKEN_COUNT = "token_count"
    const val KEY_TRUNCATED = "truncated"
    const val KEY_INFERENCE_MS = "inference_ms"
    const val KEY_ENTITIES = "entities"
    const val KEY_ENTITY_TYPE = "entity_type"
    const val KEY_RAW_TEXT = "raw_text"
    const val KEY_NORMALIZED_VALUE = "normalized_value"
    const val KEY_START_OFFSET = "start_offset"
    const val KEY_END_OFFSET = "end_offset"
    const val KEY_FAILURE_CODE = "failure_code"
    const val KEY_FAILURE_MESSAGE = "failure_message"
}
