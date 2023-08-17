use std::sync::{Arc, Mutex};
use crate::device::Device;
use std::sync::mpsc::{Receiver, SyncSender, TryRecvError, TrySendError};
use std::{path, thread};
#[cfg(not(target_env = "musl"))]
use cpal::traits::{HostTrait, DeviceTrait, StreamTrait};

pub enum GBEvent {
    KeyUp(crate::KeypadKey),
    KeyDown(crate::KeypadKey),
    SpeedUp,
    SpeedDown,
    Stop
}

fn warn(message: &str) {
    eprintln!("{}", message);
}

pub fn construct_cpu(romdata: Vec<u8>, savepath: Option<path::PathBuf>, classic_mode: bool, output_serial: bool, output_printer: bool, skip_checksum: bool) -> Option<Box<Device>> {
    let opt_c = match classic_mode {
        true => Device::new(romdata, savepath, skip_checksum),
        false => Device::new_cgb(romdata, savepath, skip_checksum),
    };
    let mut c = match opt_c
    {
        Ok(cpu) => { cpu },
        Err(message) => { warn(message); return None; },
    };

    if output_printer {
        c.attach_printer();
    }
    else {
        c.set_stdout(output_serial);
    }

    Some(Box::new(c))
}

pub fn run_cpu(mut cpu: Box<Device>, sender: SyncSender<Vec<u8>>, receiver: Receiver<GBEvent>) {
    let periodic = timer_periodic(16);
    let mut limit_speed = true;

    let waitticks = (4194304f64 / 1000.0 * 16.0).round() as u32;
    let mut ticks = 0;

    'outer: loop {
        while ticks < waitticks {
            ticks += cpu.do_cycle();
            if cpu.check_and_reset_gpu_updated() {
                let data = cpu.get_gpu_data().to_vec();
                if let Err(TrySendError::Disconnected(..)) = sender.try_send(data) {
                    break 'outer;
                }
            }
        }

        ticks -= waitticks;

        'recv: loop {
            match receiver.try_recv() {
                Ok(event) => {
                    match event {
                        GBEvent::KeyUp(key) => cpu.keyup(key),
                        GBEvent::KeyDown(key) => cpu.keydown(key),
                        GBEvent::SpeedUp => limit_speed = false,
                        GBEvent::SpeedDown => { limit_speed = true; cpu.sync_audio(); }
                        GBEvent::Stop => break 'outer
                    }
                },
                Err(TryRecvError::Empty) => break 'recv,
                Err(TryRecvError::Disconnected) => break 'outer,
            }
        }

        if limit_speed { let _ = periodic.recv(); }
    }
}

fn timer_periodic(ms: u64) -> Receiver<()> {
    let (tx, rx) = std::sync::mpsc::sync_channel(1);
    thread::spawn(move || {
        loop {
            thread::sleep(std::time::Duration::from_millis(ms));
            if tx.send(()).is_err() {
                break;
            }
        }
    });
    rx
}

#[cfg(not(target_env = "musl"))]
pub struct CpalPlayer {
    buffer: Arc<Mutex<Vec<(f32, f32)>>>,
    sample_rate: u32,
}

#[cfg(not(target_env = "musl"))]
impl CpalPlayer {
    pub(crate) fn get() -> Option<(CpalPlayer, cpal::Stream)> {
        let device = match cpal::default_host().default_output_device() {
            Some(e) => e,
            None => return None,
        };

        // We want a config with:
        // chanels = 2
        // SampleFormat F32
        // Rate at around 44100

        let wanted_samplerate = cpal::SampleRate(44100);
        let supported_configs = match device.supported_output_configs() {
            Ok(e) => e,
            Err(_) => return None,
        };
        let mut supported_config = None;
        for f in supported_configs {
            if f.channels() == 2 && f.sample_format() == cpal::SampleFormat::F32 {
                if f.min_sample_rate() <= wanted_samplerate && wanted_samplerate <= f.max_sample_rate() {
                    supported_config = Some(f.with_sample_rate(wanted_samplerate));
                }
                else {
                    supported_config = Some(f.with_max_sample_rate());
                }
                break;
            }
        }
        if supported_config.is_none() {
            return None;
        }

        let selected_config = supported_config.unwrap();

        let sample_format = selected_config.sample_format();
        let config : cpal::StreamConfig = selected_config.into();

        let err_fn = |err| eprintln!("An error occurred on the output audio stream: {}", err);

        let shared_buffer = Arc::new(Mutex::new(Vec::new()));
        let stream_buffer = shared_buffer.clone();

        let player = CpalPlayer {
            buffer: shared_buffer,
            sample_rate: config.sample_rate.0,
        };

        let stream = match sample_format {
            cpal::SampleFormat::F32 => device.build_output_stream(&config, move|data: &mut [f32], _callback_info: &cpal::OutputCallbackInfo| cpal_thread(data, &stream_buffer), err_fn),
            cpal::SampleFormat::U16 => device.build_output_stream(&config, move|data: &mut [u16], _callback_info: &cpal::OutputCallbackInfo| cpal_thread(data, &stream_buffer), err_fn),
            cpal::SampleFormat::I16 => device.build_output_stream(&config, move|data: &mut [i16], _callback_info: &cpal::OutputCallbackInfo| cpal_thread(data, &stream_buffer), err_fn),
        }.unwrap();

        stream.play().unwrap();

        Some((player, stream))
    }
}

#[cfg(not(target_env = "musl"))]
fn cpal_thread<T: cpal::Sample>(outbuffer: &mut[T], audio_buffer: &Arc<Mutex<Vec<(f32, f32)>>>) {
    let mut inbuffer = audio_buffer.lock().unwrap();
    let outlen =  ::std::cmp::min(outbuffer.len() / 2, inbuffer.len());
    for (i, (in_l, in_r)) in inbuffer.drain(..outlen).enumerate() {
        outbuffer[i*2] = cpal::Sample::from(&in_l);
        outbuffer[i*2+1] = cpal::Sample::from(&in_r);
    }
}

#[cfg(not(target_env = "musl"))]
impl crate::AudioPlayer for CpalPlayer {
    fn play(&mut self, buf_left: &[f32], buf_right: &[f32]) {
        debug_assert!(buf_left.len() == buf_right.len());

        let mut buffer = self.buffer.lock().unwrap();

        for (l, r) in buf_left.iter().zip(buf_right) {
            if buffer.len() > self.sample_rate as usize {
                // Do not fill the buffer with more than 1 second of data
                // This speeds up the resync after the turning on and off the speed limiter
                return
            }
            buffer.push((*l, *r));
        }
    }

    fn samples_rate(&self) -> u32 {
        self.sample_rate
    }

    fn underflowed(&self) -> bool {
        (*self.buffer.lock().unwrap()).len() == 0
    }
}