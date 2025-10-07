import time

class Timer:
    def __init__(self, name: str = None):
        self.name = name
        self.start_time = None

    def __enter__(self):
        self.start_time = time.perf_counter()
        return self  # 可以在 with 块里使用 Timer 实例

    def __exit__(self, exc_type, exc_value, traceback):
        elapsed = (time.perf_counter() - self.start_time) * 1000  # 毫秒
        if self.name:
            print(f"[{self.name}] 耗时: {elapsed:.3f} ms")
        else:
            print(f"耗时: {elapsed:.3f} ms")

    def start(self):
        """开始计时"""
        self.start_time = time.perf_counter()  # 高精度计时器

    def stop(self):
        """结束计时并返回耗时（毫秒）"""
        if self.start_time is None:
            raise RuntimeError("Timer has not been started.")
        elapsed = (time.perf_counter() - self.start_time) * 1000  # 转换为毫秒
        self.start_time = None  # 重置
        return elapsed