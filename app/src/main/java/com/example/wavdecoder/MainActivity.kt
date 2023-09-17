package com.example.wavdecoder

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedInputStream


class MainActivity : AppCompatActivity() {
    private val wavAfskDecoder = WavAfskDecoder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // select necessary file here
        val stream = BufferedInputStream(resources.openRawResource(R.raw.file_1))
        findViewById<TextView>(R.id.decodedText).text =
            wavAfskDecoder.getDecodedMessage(stream)
    }
}