package com.summer.classifier.ml.util

object Constants {
    const val TOKEN_MAX_LENGTH = 128
    const val CLS_TOKEN = "[CLS]"
    const val UNKNOWN_TOKEN = "[UNK]"
    const val SEPARATOR_TOKEN = "[SEP]"
    const val PADDING_TOKEN = "[PAD]"

    const val MODEL_FILE_NAME = "mobile_bert.onnx"
    const val TOKENIZER_FILE_NAME = "tokenizer_vocab.json"
    const val LABEL_ENCODER_FILE_NAME = "label_encoder.json"
    const val VOCAB = "vocab"
    const val INVERSE_VOCAB = "inv_vocab"

    const val MESSAGE_SENDER = "Sender"
    const val MESSAGE = "message"
    const val SEPARATOR = "|"

    const val INPUT_IDS = "input_ids"
    const val ATTENTION_MASK = "attention_mask"
}