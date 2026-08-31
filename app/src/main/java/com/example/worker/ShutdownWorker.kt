package com.example.worker

import android.content.Context
import android.os.Process
import androidx.work.Worker
import androidx.work.WorkerParameters

class ShutdownWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        // Shut down the application process
        Process.killProcess(Process.myPid())
        return Result.success()
    }
}
