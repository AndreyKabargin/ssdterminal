package com.example.ssd;

import static androidx.core.math.MathUtils.clamp;

import android.Manifest;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.graphics.drawable.BitmapDrawable;
import android.media.Image;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.renderscript.RenderScript;
import android.util.Base64;
import android.util.Size;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;

import com.loopj.android.http.RequestHandle;
import com.loopj.android.http.RequestParams;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.params.HttpConnectionParams;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE = 123;
    private Button takeFoto;
    private Button saveFoto;
    private Button deleteFoto;
    private PreviewView preView;
    private ImageCapture imageCapture;
    private ImageView capture;
    private TextView textView;
    private int codeEquipment = -1;
    private String nameEquipment = "";
    private TextView textMessage;

    @SuppressLint({"MissingInflatedId", "WrongViewCast"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preView = findViewById(R.id.preView);
        capture = findViewById(R.id.capture);
        takeFoto = findViewById(R.id.takeFoto);
        saveFoto = findViewById(R.id.saveFoto);
        deleteFoto = findViewById(R.id.deleteFoto);
        textView = findViewById(R.id.textView);
        textMessage = findViewById(R.id.textMessage);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.REQUEST_INSTALL_PACKAGES) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ActivityCompat.requestPermissions(this,
                        new String[] { Manifest.permission.CAMERA, Manifest.permission.INTERNET, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.REQUEST_INSTALL_PACKAGES},
                        REQUEST_CODE);
            }
        }

        takeFoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ContentValues contentValues = new ContentValues();
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, new Date().toString());
                contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
                contentValues.put(MediaStore.MediaColumns.RESOLUTION, "19201080");

                ImageCapture.OutputFileOptions options = new ImageCapture.OutputFileOptions.Builder(getContentResolver(), MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues).build();

                imageCapture.takePicture(ContextCompat.getMainExecutor(MainActivity.this), new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy image) {
                        super.onCaptureSuccess(image);

                        capture.setImageBitmap(imageToBitmap(image.getImage()));
                        capture.setVisibility(View.VISIBLE);
                        takeFoto.setVisibility(View.INVISIBLE);
                        saveFoto.setVisibility(View.VISIBLE);
                        deleteFoto.setVisibility(View.VISIBLE);

                        image.close();
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        super.onError(exception);

                        Toast.makeText(MainActivity.this, "Не удалось сохранить фото на телефон!",
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        });

        saveFoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                textMessage.setText("Идет отправка данных...");

                takeFoto.setEnabled(false);
                deleteFoto.setEnabled(false);
                saveFoto.setEnabled(false);

                String file = bitMapToString(((BitmapDrawable)capture.getDrawable()).getBitmap());

                String url = "http://172.16.30.10/ssd/hs/ProductionAPI/ThirdPrinter";

                RequestParams params = new RequestParams();

                JSONObject jsonObject = new JSONObject();
                try {
                    jsonObject.put("File", file);
                    jsonObject.put("Code", codeEquipment);
                }
                catch (JSONException ex) {

                }

                params.put("JSON", jsonObject);

                AsyncHttpClient client = new AsyncHttpClient();
                client.setBasicAuth("Obmen", "Obmen");
                client.post(url, params, new AsyncHttpResponseHandler(){

                    @Override
                    public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {

                        if(statusCode == 200) {

                            capture.setVisibility(View.INVISIBLE);
                            saveFoto.setVisibility(View.INVISIBLE);

                            textMessage.setText("");
                            takeFoto.setEnabled(true);
                            deleteFoto.setEnabled(true);
                            saveFoto.setEnabled(true);
                        }

                    }

                    @Override
                    public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {

                        Toast.makeText(MainActivity.this, "Не удалось сохранить фото в 1с!",
                                Toast.LENGTH_LONG).show();

                        textMessage.setText("");
                        takeFoto.setEnabled(true);
                        deleteFoto.setEnabled(true);
                        saveFoto.setEnabled(true);
                    }
                });
            }
        });

        deleteFoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                capture.setVisibility(View.INVISIBLE);
                saveFoto.setVisibility(View.INVISIBLE);
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_CODE:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startCamera();
                } else {
                }
                return;
        }
    }

    void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {

            }
        }, ContextCompat.getMainExecutor(this));
    }

    @SuppressLint("RestrictedApi")
    void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder()
                .build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        preview.setSurfaceProvider(preView.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder().build();
        cameraProvider.bindToLifecycle((LifecycleOwner)this, cameraSelector, preview, imageCapture);

        BarcodeScannerOptions options =
                new BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(
                                Barcode.FORMAT_QR_CODE,
                                Barcode.FORMAT_CODE_39)
                        .build();
        BarcodeScanner scanner = BarcodeScanning.getClient(options);

        ImageAnalysis imageAnalysis =
                new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(MainActivity.this), new ImageAnalysis.Analyzer() {
            @Override
            public void analyze(@NonNull ImageProxy imageProxy) {

                Image mediaImage = imageProxy.getImage();
                InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

                scanner.process(image).addOnSuccessListener(new OnSuccessListener<List<Barcode>>() {
                    @Override
                    public void onSuccess(List<Barcode> barcodes) {

                        for (Barcode barcode: barcodes) {

                           // Toast.makeText(MainActivity.this, barcode.getDisplayValue(),
                           //         Toast.LENGTH_LONG).show();
                            try {
                                codeEquipment = Integer.parseInt(barcode.getDisplayValue());
                            }catch (Exception ex) {
                                Toast.makeText(MainActivity.this, "Не удалось распознать QR-код!",
                                        Toast.LENGTH_LONG).show();
                            }

                            if(codeEquipment > -1) {

                                String url = "http://172.16.30.10/ssd/hs/ProductionAPI/WorkCenter";


                                RequestParams params = new RequestParams();

                                params.put("Code", codeEquipment);

                                AsyncHttpClient client = new AsyncHttpClient();
                                client.setBasicAuth("Obmen", "Obmen");

                                client.post(url, params, new AsyncHttpResponseHandler(){

                                    @Override
                                    public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                                        if(statusCode == 200) {
                                            nameEquipment = new String(responseBody, StandardCharsets.UTF_8);
                                        }
                                    }

                                    @Override
                                    public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                                        Toast.makeText(MainActivity.this, "Нет связи с 1с!",
                                                Toast.LENGTH_LONG).show();
                                    }
                                });

                        }

                        }
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(MainActivity.this, "Не работает сканер!",
                                Toast.LENGTH_LONG).show();
                    }
                }).addOnCompleteListener(new OnCompleteListener<List<Barcode>>() {
                    @Override
                    public void onComplete(@NonNull Task<List<Barcode>> task) {
                        imageProxy.close();

                        if(!nameEquipment.equals("")) {
                            cameraProvider.unbind(imageAnalysis);
                            takeFoto.setVisibility(View.VISIBLE);
                            //saveFoto.setVisibility(View.VISIBLE);
                            //deleteFoto.setVisibility(View.VISIBLE);
                            textView.setText(nameEquipment);

                        }


                    }
                });
            }
        });
        cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, imageAnalysis);
    }

    public Bitmap imageToBitmap(Image image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.capacity()];
        buffer.get(bytes);

        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);
    }

    public String bitMapToString(Bitmap bitmap) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 40, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream .toByteArray();

        return Base64.encodeToString(byteArray, Base64.DEFAULT);
    }

}
