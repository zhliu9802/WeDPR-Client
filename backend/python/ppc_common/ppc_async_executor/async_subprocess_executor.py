import multiprocessing
import threading
import time
from typing import Callable

from ppc_common.ppc_async_executor.async_executor import AsyncExecutor


class AsyncSubprocessExecutor(AsyncExecutor):
    def __init__(self, logger):
        self.logger = logger
        self.processes = {}
        self.lock = threading.Lock()
        self._cleanup_thread = threading.Thread(target=self._loop_cleanup)
        self._cleanup_thread.daemon = True
        self._cleanup_thread.start()

    def execute(self, target_id: str, target: Callable, on_target_finish: Callable[[str, bool, Exception], None],
                args=()):
        process = multiprocessing.Process(target=target, args=args)
        process.start()
        with self.lock:
            self.processes[target_id] = process

    def kill(self, target_id: str):
        with self.lock:
            if target_id not in self.processes:
                return False
            else:
                process = self.processes[target_id]

        process.terminate()
        self.logger.info(f"Target {target_id} has been terminated!")
        return True

    def kill_all(self):
        with self.lock:
            keys = self.processes.keys()

        for target_id in keys:
            self.kill(target_id)

    def _loop_cleanup(self):
        while True:
            self._cleanup_finished_processes()
            time.sleep(3)

    def _cleanup_finished_processes(self):
        with self.lock:
            finished_processes = [
                (target_id, proc) for target_id, proc in self.processes.items() if not proc.is_alive()]

        for target_id, process in finished_processes:
            with self.lock:
                process.join()  # 确保进程资源释放
                del self.processes[target_id]
            self.logger.info(f"Cleanup finished process {target_id}")

    def __del__(self):
        self.kill_all()
