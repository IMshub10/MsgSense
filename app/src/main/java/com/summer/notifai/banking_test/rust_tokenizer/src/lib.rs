use jni::objects::{JClass, JObjectArray, JString};
use jni::sys::{jintArray, jsize};
use jni::JNIEnv;
use std::sync::Mutex;
use tokenizers::tokenizer::Tokenizer;

static TOKENIZER: Mutex<Option<Tokenizer>> = Mutex::new(None);

/// Load tokenizer from a JSON file path. Call once at startup.
#[no_mangle]
pub extern "system" fn Java_com_summer_notifai_banking_1test_HfTokenizerBridge_nativeLoadTokenizer(
    mut env: JNIEnv,
    _class: JClass,
    path: JString,
) {
    let path_str: String = env.get_string(&path).expect("Bad path string").into();
    let tok = Tokenizer::from_file(&path_str).expect("Failed to load tokenizer.json");
    let mut guard = TOKENIZER.lock().unwrap();
    *guard = Some(tok);
}

/// Encode a pre-tokenized word list. Returns a flat int array:
///   [input_ids... (128), attention_mask... (128), word_ids... (128)]
/// Total length = 384.
#[no_mangle]
pub extern "system" fn Java_com_summer_notifai_banking_1test_HfTokenizerBridge_nativeEncode(
    mut env: JNIEnv,
    _class: JClass,
    words_array: JObjectArray,
) -> jintArray {
    let guard = TOKENIZER.lock().unwrap();
    let tok = guard.as_ref().expect("Tokenizer not loaded — call nativeLoadTokenizer first");

    let len = env.get_array_length(&words_array).unwrap() as usize;
    let mut words: Vec<String> = Vec::with_capacity(len);
    for i in 0..len {
        let jstr: JString = env
            .get_object_array_element(&words_array, i as jsize)
            .unwrap()
            .into();
        let s: String = env.get_string(&jstr).unwrap().into();
        words.push(s);
    }

    let encoding = tok
        .encode(words, true)
        .expect("Encoding failed");

    let ids = encoding.get_ids();
    let mask = encoding.get_attention_mask();
    let word_ids_raw = encoding.get_word_ids();

    let max_len = 128;
    let mut result = vec![0i32; max_len * 3];

    for i in 0..max_len.min(ids.len()) {
        result[i] = ids[i] as i32;
        result[max_len + i] = mask[i] as i32;
        result[max_len * 2 + i] = word_ids_raw
            .get(i)
            .and_then(|w| *w)
            .map(|w| w as i32)
            .unwrap_or(-1);
    }

    let out = env.new_int_array(result.len() as jsize).unwrap();
    env.set_int_array_region(&out, 0, &result).unwrap();
    out.into_raw()
}

/// Free the tokenizer (optional cleanup).
#[no_mangle]
pub extern "system" fn Java_com_summer_notifai_banking_1test_HfTokenizerBridge_nativeFree(
    _env: JNIEnv,
    _class: JClass,
) {
    let mut guard = TOKENIZER.lock().unwrap();
    *guard = None;
}
