import time

class Timer:
    def __init__(self, name: str = None):
        self.name = name
        self.start_time = None

    def __enter__(self):
        if self.name:
            print(f"[{self.name}] 开始计时")
        else:
            print(f"开始计时")
        self.start_time = time.perf_counter()
        return self  # 可以在 with 块里使用 Timer 实例

    def __exit__(self, exc_type, exc_value, traceback):
        elapsed = (time.perf_counter() - self.start_time) * 1000  # 毫秒
        if self.name:
            print(f"[{self.name}] 耗时: {elapsed:.3f} ms")
        else:
            print(f"耗时: {elapsed:.3f} ms")

    def start(self):
        self.__enter__()

    def stop(self):
        self.__exit__()