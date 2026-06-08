import threading
from typing import Dict

from readerwriterlock import rwlock


class ThreadEventManager:
    def __init__(self):
        # Event清理由TaskManager完成
        self.events: Dict[str, threading.Event] = {}
        self.rw_lock = rwlock.RWLockWrite()

    def add_event(self, target_id: str, event: threading.Event) -> None:
        with self.rw_lock.gen_wlock():
            self.events[target_id] = event

    def remove_event(self, target_id: str):
        with self.rw_lock.gen_wlock():
            if target_id in self.events:
                del self.events[target_id]

    def set_event(self, target_id: str):
        with self.rw_lock.gen_wlock():
            if target_id in self.events:
                self.events[target_id].set()
            else:
                raise KeyError(f"Target id {target_id} not found")

    def event_status(self, target_id: str) -> bool:
        with self.rw_lock.gen_rlock():
            if target_id in self.events:
                return self.events[target_id].is_set()
            else:
                return False
