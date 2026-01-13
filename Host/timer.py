import time

class Timer:
    def __init__(self, name: str = None):
        self.name = name
        self.start_time = None

    def __enter__(self):
        if self.name:
            print(f"[{self.name}] Start Timer")
        else:
            print(f"Start Timer")
        self.start_time = time.perf_counter()
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        elapsed = (time.perf_counter() - self.start_time) * 1000 
        if self.name:
            print(f"[{self.name}] Time Usage: {elapsed:.3f} ms")
        else:
            print(f"Time Usage: {elapsed:.3f} ms")

    def start(self):
        self.__enter__()

    def stop(self):
        self.__exit__()