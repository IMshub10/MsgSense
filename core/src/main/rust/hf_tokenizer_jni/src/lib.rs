use jni::objects::{JClass, JObjectArray, JString};
use jni::sys::{jint, jintArray};
use jni::JNIEnv;
use std::sync::{Mutex, OnceLock};
use tokenizers::tokenizer::{PaddingDirection, PaddingParams, PaddingStrategy, Tokenizer};
use tokenizers::utils::truncation::{TruncationDirection, TruncationParams, TruncationStrategy};

static TOKENIZER: OnceLock<Mutex<Option<Tokenizer>>> = OnceLock::new();


fn describe_text(text: &str) -> String {
    let codepoints = text
        .chars()
        .map(|c| format!("U+{:04X}", c as u32))
        .collect::<Vec<_>>()
        .join(" ");
    let utf8 = text
        .as_bytes()
        .iter()
        .map(|b| format!("{:02x}", b))
        .collect::<Vec<_>>()
        .join(" ");
    format!(
        "len={};code_points={};utf8_len={};codepoints={};utf8={}",
        text.chars().count(),
        text.chars().count(),
        text.as_bytes().len(),
        codepoints,
        utf8
    )
}

fn tokenizer_slot() -> &'static Mutex<Option<Tokenizer>> {
    TOKENIZER.get_or_init(|| Mutex::new(None))
}

fn throw_runtime_exception(env: &mut JNIEnv, message: impl AsRef<str>) {
    let _ = env.throw_new("java/lang/RuntimeException", message.as_ref());
}

fn fixed_tokenizer(base: &Tokenizer, max_length: usize) -> Result<Tokenizer, String> {
    let mut tokenizer = base.clone();
    tokenizer
        .with_truncation(Some(TruncationParams {
            direction: TruncationDirection::Right,
            max_length,
            strategy: TruncationStrategy::LongestFirst,
            stride: 0,
        }))
        .map_err(|e| format!("Failed to configure truncation: {}", e))?;
    tokenizer.with_padding(Some(PaddingParams {
        strategy: PaddingStrategy::Fixed(max_length),
        direction: PaddingDirection::Right,
        pad_to_multiple_of: None,
        pad_id: 0,
        pad_type_id: 0,
        pad_token: "<pad>".to_string(),
    }));
    Ok(tokenizer)
}

fn to_fixed_len_u32(mut values: Vec<u32>, max_length: usize, pad: u32) -> Vec<jint> {
    if values.len() > max_length {
        values.truncate(max_length);
    }
    if values.len() < max_length {
        values.resize(max_length, pad);
    }
    values.into_iter().map(|v| v as jint).collect()
}

fn to_fixed_len_mask(mut values: Vec<u32>, max_length: usize) -> Vec<jint> {
    if values.len() > max_length {
        values.truncate(max_length);
    }
    if values.len() < max_length {
        values.resize(max_length, 0);
    }
    values.into_iter().map(|v| v as jint).collect()
}

fn load_tokenizer(path: &str) -> Result<(), String> {
    let tokenizer = Tokenizer::from_file(path)
        .map_err(|e| format!("Failed to load tokenizer from {}: {}", path, e))?;
    let mut slot = tokenizer_slot()
        .lock()
        .map_err(|_| "Tokenizer mutex poisoned".to_string())?;
    *slot = Some(tokenizer);
    Ok(())
}

fn encode_single(text: &str, max_length: usize) -> Result<Vec<jint>, String> {
    let slot = tokenizer_slot()
        .lock()
        .map_err(|_| "Tokenizer mutex poisoned".to_string())?;
    let base = slot
        .as_ref()
        .ok_or_else(|| "Tokenizer not loaded".to_string())?;

    let tokenizer = fixed_tokenizer(base, max_length)?;
    let encoding = tokenizer
        .encode(text, true)
        .map_err(|e| format!("Failed to encode text: {}", e))?;

    let mut flat = Vec::with_capacity(max_length * 3);
    flat.extend(to_fixed_len_u32(encoding.get_ids().to_vec(), max_length, 0));
    flat.extend(to_fixed_len_mask(
        encoding.get_attention_mask().to_vec(),
        max_length,
    ));
    flat.extend(to_fixed_len_u32(
        encoding.get_type_ids().to_vec(),
        max_length,
        0,
    ));
    Ok(flat)
}

#[no_mangle]
pub extern "system" fn Java_com_summer_core_ml_tokenizer_HfTokenizerBridge_nativeLoadTokenizer(
    mut env: JNIEnv,
    _class: JClass,
    tokenizer_path: JString,
) {
    let path = match env.get_string(&tokenizer_path) {
        Ok(s) => s.to_string_lossy().into_owned(),
        Err(e) => {
            throw_runtime_exception(&mut env, format!("Failed to read tokenizer path: {}", e));
            return;
        }
    };

    if let Err(e) = load_tokenizer(&path) {
        throw_runtime_exception(&mut env, e);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_summer_core_ml_tokenizer_HfTokenizerBridge_nativeEncode(
    mut env: JNIEnv,
    _class: JClass,
    texts: JObjectArray,
    max_length: jint,
) -> jintArray {
    let len = match env.get_array_length(&texts) {
        Ok(v) => v,
        Err(e) => {
            throw_runtime_exception(&mut env, format!("Failed to read text array length: {}", e));
            return std::ptr::null_mut();
        }
    };

    if len <= 0 {
        return match env.new_int_array(0) {
            Ok(arr) => arr.into_raw(),
            Err(_) => std::ptr::null_mut(),
        };
    }

    let first = match env.get_object_array_element(&texts, 0) {
        Ok(obj) => JString::from(obj),
        Err(e) => {
            throw_runtime_exception(&mut env, format!("Failed to read first text element: {}", e));
            return std::ptr::null_mut();
        }
    };

    let text = match env.get_string(&first) {
        Ok(s) => s.to_string_lossy().into_owned(),
        Err(e) => {
            throw_runtime_exception(&mut env, format!("Failed to read text: {}", e));
            return std::ptr::null_mut();
        }
    };

    let max_length = usize::try_from(max_length).unwrap_or(128);
    let encoded = match encode_single(&text, max_length) {
        Ok(v) => v,
        Err(e) => {
            throw_runtime_exception(&mut env, e);
            return std::ptr::null_mut();
        }
    };

    let out = match env.new_int_array(encoded.len() as i32) {
        Ok(arr) => arr,
        Err(e) => {
            throw_runtime_exception(&mut env, format!("Failed to allocate output array: {}", e));
            return std::ptr::null_mut();
        }
    };

    if let Err(e) = env.set_int_array_region(&out, 0, &encoded) {
        throw_runtime_exception(&mut env, format!("Failed to write output array: {}", e));
        return std::ptr::null_mut();
    }

    out.into_raw()
}


#[no_mangle]
pub extern "system" fn Java_com_summer_core_ml_tokenizer_HfTokenizerBridge_nativeDebugDescribeString(
    mut env: JNIEnv,
    _class: JClass,
    text: JString,
) -> jni::sys::jstring {
    let text = match env.get_string(&text) {
        Ok(s) => s.to_string_lossy().into_owned(),
        Err(e) => {
            throw_runtime_exception(&mut env, format!("Failed to read debug text: {}", e));
            return std::ptr::null_mut();
        }
    };

    let desc = describe_text(&text);
    match env.new_string(desc) {
        Ok(s) => s.into_raw(),
        Err(e) => {
            throw_runtime_exception(&mut env, format!("Failed to build debug string: {}", e));
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_summer_core_ml_tokenizer_HfTokenizerBridge_nativeFree(
    _env: JNIEnv,
    _class: JClass,
) {
    if let Ok(mut slot) = tokenizer_slot().lock() {
        *slot = None;
    }
}
