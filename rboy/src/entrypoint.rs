use crate::device::Device;
use std::sync::mpsc::{Receiver, SyncSender, TryRecvError, TrySendError};
use std::thread;

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

pub fn construct_cpu(filename: &str, classic_mode: bool, output_serial: bool, output_printer: bool, skip_checksum: bool) -> Option<Box<Device>> {
    let opt_c = match classic_mode {
        true => Device::new(filename, skip_checksum),
        false => Device::new_cgb(filename, skip_checksum),
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