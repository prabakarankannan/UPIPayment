package com.montee.upipayment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.text.method.DigitsKeyListener
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.integration.android.IntentResult
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.montee.upipayment.R
import kotlinx.android.synthetic.main.activity_main.*
import java.text.DecimalFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private var mVpa: String? = null
    private var mName: String? = null

    private var mMerchantCode: String? = null
    private var mOrganizationId: String? = null

    private var mNote: String? = null
    private var mQRHasNote: Boolean = false

    private var mAmount: String? = null
    private var mQRHasAmount: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        et_note.inputType = InputType.TYPE_NULL
        et_amount.inputType = InputType.TYPE_NULL

        btn_scan.setOnClickListener {
            val integrator = IntentIntegrator(this)
            integrator.captureActivity = CameraCaptureActivity::class.java
            integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            integrator.setPrompt("Scan the Merchant's QR Code")
            integrator.setOrientationLocked(true)
            integrator.setBarcodeImageEnabled(true)
            integrator.setBeepEnabled(false)
            integrator.initiateScan()
        }

        btn_pay.setOnClickListener {
            when {
                mVpa.isNullOrEmpty() || mMerchantCode.isNullOrEmpty() -> {
                    Toast.makeText(
                        this,
                        "Please scan a QR code before trying to pay",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                et_amount.text.isNullOrEmpty() -> {
                    til_amount.error = "Please enter an amount"
                }
                else -> {
                    if (!mQRHasAmount) {
                        mAmount = et_amount.text.toString()
                    }

                    val amountCheck = mAmount?.toDoubleOrNull()
                    if (amountCheck != null) {
                        val df = DecimalFormat("0.00")
                        mAmount = df.format(amountCheck)

                        initiatePayment()
                    } else {
                        til_amount.error = "Please enter a valid amount"

                        et_amount.setText("")
                        mQRHasAmount = false
                        mAmount = null
                    }
                }
            }
        }
    }

    private fun initiatePayment() {

        if (!mQRHasNote) {
            mNote = when {
                et_note.text.isNullOrEmpty() -> "Payment from LFYD"
                else -> et_note.text.toString()
            }
            et_note.setText(mNote)
        }

        val tid =
            (System.currentTimeMillis() / 1000).toString() + (mName?.split(" ")?.get(0) ?: mName)

        mNote?.replace(" ", "%20")
        mName?.replace(" ", "%20")

        val payUri = Uri.Builder()
        payUri.scheme(UPI).authority(PAY)
        payUri.appendQueryParameter(VPA, mVpa)
        payUri.appendQueryParameter(NAME, mName)
        payUri.appendQueryParameter(TRANSACTION_ID, tid)
        payUri.appendQueryParameter(MERCHANT_CODE, mMerchantCode)
        payUri.appendQueryParameter(ORGANIZATION_ID, mOrganizationId)
        payUri.appendQueryParameter(TRANSACTION_REFERENCE_ID, tid)
        payUri.appendQueryParameter(NOTE, mNote)
        payUri.appendQueryParameter(AMOUNT, mAmount)
        payUri.appendQueryParameter(CURRENCY, INR)

        val paymentIntent = Intent(Intent.ACTION_VIEW)
        paymentIntent.data = payUri.build()

        val chooser = Intent.createChooser(paymentIntent, "Pay With")

        if (null != chooser.resolveActivity(packageManager)) {
            startActivityForResult(chooser, UPI_PAYMENT)
        } else {
            Toast.makeText(
                this,
                "No UPI app found! Please install one to proceed!",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            if (result.contents == null) {
                Toast.makeText(
                    this,
                    "QR Code could not be scanned",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                parseQrCode(result)
            }
        } else if (requestCode == UPI_PAYMENT) {
            when {
                (resultCode == RESULT_OK || resultCode == 11) && (data != null) -> {
                    val response = data.getStringExtra("response")
                    if (response != null) {
                        val transactionDetails = getTransactionDetails(response)

                        Log.d("Transaction Details", transactionDetails.toString())

                        when (transactionDetails.status.toLowerCase(Locale.ROOT)) {
                            "success", "submitted", "pending" -> {
                                Toast.makeText(
                                    this,
                                    "Transaction Completed.",
                                    Toast.LENGTH_LONG
                                ).show()

                                iv_qr_code.setImageResource(R.drawable.ic_baseline_check_circle_outline_24)
                            }
                            else -> {
                                Toast.makeText(
                                    this,
                                    "Transaction Failed. Please try again.",
                                    Toast.LENGTH_LONG
                                ).show()

                                iv_qr_code.setImageResource(R.drawable.ic_baseline_error_outline_24)
                            }
                        }

                        setNull()

                        et_note.setText("")
                        et_amount.setText("")
                    }
                }
                else -> {
                    super.onActivityResult(requestCode, resultCode, data)
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun setNull() {
        mVpa = null
        mName = null

        mMerchantCode = null
        mOrganizationId = null

        mQRHasAmount = false
        mAmount = null

        mQRHasNote = false
        mNote = null

        tv_merchant_details.text = ""
    }

    private fun parseQrCode(result: IntentResult) {
        val uri = Uri.parse(result.contents)
        mVpa = uri.getQueryParameter(VPA)
        mName = uri.getQueryParameter(NAME)
        mMerchantCode = uri.getQueryParameter(MERCHANT_CODE)
        mOrganizationId = uri.getQueryParameter(ORGANIZATION_ID)
        mNote = uri.getQueryParameter(NOTE)
        mAmount = uri.getQueryParameter(AMOUNT)

        if (mVpa.isNullOrEmpty() || mVpa == "null" || mVpa == "undefined" || mMerchantCode.isNullOrEmpty() || mMerchantCode == "null" || mMerchantCode == "undefined") {
            setNull()

            Toast.makeText(
                this,
                "Please scan a valid QR code",
                Toast.LENGTH_LONG
            ).show()
        } else {
            tv_merchant_details.text = mName

            if (mNote.isNullOrEmpty() || mNote.equals("null")) {
                mQRHasNote = false
                et_note.inputType = InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE
            } else {
                mQRHasNote = true
                et_note.inputType = InputType.TYPE_NULL
                et_note.setText(mNote)
            }

            if (mAmount.isNullOrEmpty() || mAmount.equals("null")) {
                mQRHasAmount = false
                et_amount.inputType = InputType.TYPE_NUMBER_FLAG_DECIMAL
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    et_amount.keyListener = DigitsKeyListener.getInstance(Locale.ROOT, false, true)
                }
            } else {
                mQRHasAmount = true
                et_amount.inputType = InputType.TYPE_NULL
                et_amount.setText(mAmount)
            }

            val bitmap = BarcodeEncoder().encodeBitmap(
                result.contents,
                BarcodeFormat.QR_CODE,
                iv_qr_code.measuredWidth,
                iv_qr_code.measuredHeight
            )
            iv_qr_code.setImageBitmap(bitmap)
        }

        Log.d(
            "QR Code",
            "VPA: $mVpa, NAME: $mName, MERCHANT CODE: $mMerchantCode, ORGANIZATION ID: $mOrganizationId, NOTE: $mNote, AMOUNT: $mAmount"
        )

        Log.d("QR Code", result.contents)
    }


    private fun getTransactionDetails(response: String): TransactionDetails {
        val params = response.split("&")
        val map = HashMap<String, String>()
        for (param in params) {
            val name = param.split("=")[0]
            val value = param.split("=")[1]
            map[name] = value
        }

        val transactionId = map["txnId"]
        val transactionRefId = map["txnRef"]
        val responseCode = map["responseCode"]
        val status = map["Status"]
        val approvalRefNo = map["ApprovalRefNo"]

        return TransactionDetails(
            transactionId.toString(),
            transactionRefId.toString(),
            responseCode.toString(),
            status.toString(),
            approvalRefNo.toString()
        )
    }

    companion object {
        private const val UPI = "upi"
        private const val PAY = "pay"

        private const val VPA = "pa"
        private const val NAME = "pn"
        private const val TRANSACTION_ID = "tid"
        private const val MERCHANT_CODE = "mc"
        private const val ORGANIZATION_ID = "orgid"
        private const val TRANSACTION_REFERENCE_ID = "tr"
        private const val NOTE = "tn"
        private const val AMOUNT = "am"

        private const val CURRENCY = "cu"
        private const val INR = "INR"

        private const val UPI_PAYMENT = 1
    }
}