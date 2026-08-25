package com.example.securelib

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.securelib.ui.theme.SecureLibTheme
import com.securelib.securecheck.SecureCheck
import com.securelib.securecheck.SecurityCheckResult

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SecureLibTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SecurityStatus(modifier = Modifier.padding(innerPadding).padding(16.dp))
                }
            }
        }

        val ss = "SDS"
    }
}

@Composable
private fun SecurityStatus(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val secureCheck = remember {
        SecureCheck.Builder(context)
            .expectedPackageName("com.example.securelib")
            // Opt-in checks — uncomment and configure to see them in action:
            //
             .addSignatureValidator(
                 expectedSha256 = "dc7f9e72c0857808421bd7a97a051c9dae03b651c53f9f4e9ce42f4807d37ec2",
             )
            //
            // .addPlayIntegrityValidator(
            //     cloudProjectNumber = 1234567890L,
            //     verifier = { token -> myBackend.verifyIntegrityToken(token) },
            // )
            .build()
    }
    var result by remember { mutableStateOf<SecurityCheckResult?>(null) }

    LaunchedEffect(secureCheck) {
        result = secureCheck.checkDetailed()
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val snapshot = result
        if (snapshot == null) {
            Text("Running security checks…")
        } else {
            Text("Overall passed: ${snapshot.passed}")
            HorizontalDivider()
            snapshot.checks.forEach { outcome ->
                val status = if (outcome.passed) "PASS" else "FAIL"
                val error = outcome.error?.let { " — $it" }.orEmpty()
                Text("$status  ${outcome.name}$error")
            }
        }
    }
}
