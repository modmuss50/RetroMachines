use std::sync::mpsc;
use std::sync::mpsc::{Receiver, Sender, SyncSender};

use jni::JNIEnv;
use jni::objects::{JByteArray, JClass, JString};
use jni::sys::{jlong, jint};

use crate::device::Device;
use crate::entrypoint::{construct_cpu, GBEvent, run_cpu};

struct Context {
    cpu_context_ptr: jlong,
    gpu_receiver: Receiver<Vec<u8>>,
    event_sender: Sender<GBEvent>
}

struct CpuContext {
    cpu: Box<Device>,
    gpu_sender: SyncSender<Vec<u8>>,
    event_receiver: Receiver<GBEvent>
}

#[no_mangle]
pub unsafe extern "system" fn Java_retromachines_rboy_RBoy_construct_1cpu<'local>(mut env: JNIEnv<'local>,
                                                              _class: JClass<'local>,
                                                              j_filename: JString<'local>) -> jlong {
    let filename: String = env
        .get_string(&j_filename)
        .expect("Couldn't get java string!")
        .into();

    let cpu = construct_cpu(
        filename.as_str(),
        true,
        true,
        false,
        false
    );
    if cpu.is_none() { return 0 as jlong; }

    let (event_sender, event_receiver) = mpsc::channel();
    let (gpu_sender, gpu_receiver) = mpsc::sync_channel(1);
    // The CPU context's ownership is moved to the cpu thread.
    let cpu_context = CpuContext {cpu: cpu.unwrap(), gpu_sender, event_receiver};
    let context = Context {cpu_context_ptr: Box::into_raw(Box::new(cpu_context)) as jlong, gpu_receiver, event_sender};

    return Box::into_raw(Box::new(context)) as jlong;
}

#[no_mangle]
pub unsafe extern "system" fn Java_retromachines_rboy_RBoy_run_1cpu<'local>(_env: JNIEnv<'local>,
                                                               _class: JClass<'local>,
                                                               context_ptr: jlong) {
    let context = &mut *(context_ptr as *mut Context);
    let context = Box::from_raw(context.cpu_context_ptr as *mut CpuContext);
    run_cpu(context.cpu, context.gpu_sender, context.event_receiver);
}

#[no_mangle]
pub unsafe extern "system" fn Java_retromachines_rboy_RBoy_get_1gpu_1data<'local>(env: JNIEnv<'local>,
                                                               _class: JClass<'local>,
                                                               context_ptr: jlong) -> JByteArray<'local> {
    let context = &mut *(context_ptr as *mut Context);
    return match context.gpu_receiver.recv() {
        Ok(data) => env.byte_array_from_slice(&data).unwrap(),
        Err(..) => env.new_byte_array(0).unwrap(), // No new screen data
    }
}

#[no_mangle]
pub unsafe extern "system" fn Java_retromachines_rboy_RBoy_send_1event<'local>(_env: JNIEnv<'local>,
                                                                               _class: JClass<'local>,
                                                                               context_ptr: jlong,
                                                                               event: jint) {
    let context = &mut *(context_ptr as *mut Context);

    let event = match event {
        KEY_A_DOWN => GBEvent::KeyDown(crate::KeypadKey::A),
        KEY_B_DOWN => GBEvent::KeyDown(crate::KeypadKey::B),
        KEY_UP_DOWN => GBEvent::KeyDown(crate::KeypadKey::Up),
        KEY_DOWN_DOWN => GBEvent::KeyDown(crate::KeypadKey::Down),
        KEY_LEFT_DOWN => GBEvent::KeyDown(crate::KeypadKey::Left),
        KEY_RIGHT_DOWN => GBEvent::KeyDown(crate::KeypadKey::Right),
        KEY_SELECT_DOWN => GBEvent::KeyDown(crate::KeypadKey::Select),
        KEY_START_DOWN => GBEvent::KeyDown(crate::KeypadKey::Start),
        KEY_A_UP => GBEvent::KeyUp(crate::KeypadKey::A),
        KEY_B_UP => GBEvent::KeyUp(crate::KeypadKey::B),
        KEY_UP_UP => GBEvent::KeyUp(crate::KeypadKey::Up),
        KEY_DOWN_UP => GBEvent::KeyUp(crate::KeypadKey::Down),
        KEY_LEFT_UP => GBEvent::KeyUp(crate::KeypadKey::Left),
        KEY_RIGHT_UP => GBEvent::KeyUp(crate::KeypadKey::Right),
        KEY_SELECT_UP => GBEvent::KeyUp(crate::KeypadKey::Select),
        KEY_START_UP => GBEvent::KeyUp(crate::KeypadKey::Start),
        STOP => GBEvent::Stop,
        SPEED_UP => GBEvent::SpeedUp,
        SPEED_DOWN => GBEvent::SpeedDown,
        _ => panic!("Unknown event"),
    };

    context.event_sender.send(event).expect("Failed to send event");
}

// Must match RBoy.java
const KEY_A_DOWN: jint = 1;
const KEY_B_DOWN: jint = 2;
const KEY_UP_DOWN: jint = 3;
const KEY_DOWN_DOWN: jint = 4;
const KEY_LEFT_DOWN: jint = 5;
const KEY_RIGHT_DOWN: jint = 6;
const KEY_SELECT_DOWN: jint = 7;
const KEY_START_DOWN: jint = 8;
const KEY_A_UP: jint = 9;
const KEY_B_UP: jint = 10;
const KEY_UP_UP: jint = 11;
const KEY_DOWN_UP: jint = 12;
const KEY_LEFT_UP: jint = 13;
const KEY_RIGHT_UP: jint = 14;
const KEY_SELECT_UP: jint = 15;
const KEY_START_UP: jint = 16;
const STOP: jint = 101;
const SPEED_UP: jint = 202;
const SPEED_DOWN: jint = 203;