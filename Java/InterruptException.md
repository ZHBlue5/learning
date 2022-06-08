#### 为什么在catch InterruptException块中调用Thread.currentThread.interrupt（）？
- 这是维持状态。
- 当你捕获InterruptException并吞下它时，你基本上阻止任何更高级别的方法/线程组注意到中断。这可能会导致问题。
- 通过调用Thread.currentThread().interrupt()，你可以设置线程的中断标志，因此更高级别的中断处理程序会注意到它并且可以正确处理它。