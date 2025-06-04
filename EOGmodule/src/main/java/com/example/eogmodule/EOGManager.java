package com.example.eogmodule;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.UUID;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;

public class EOGManager {
    private static final String TAG = "EOGManager";
    private BluetoothHelper bluetoothHelper;
    private Context context;
    private EOGEventListener eogEventListener;
    private Module module;


    // 버퍼에 저장할 샘플을 나타내는 내부 클래스
    private static class Sample {
        long timestamp; // 밀리초 단위 타임스탬프
        float x;
        float y;

        Sample(long timestamp, float x, float y) {
            this.timestamp = timestamp;
            this.x = x;
            this.y = y;
        }
    }

    // 최근 1.5초간의 샘플을 보관하는 덱
    private final Deque<Sample> buffer = new ArrayDeque<>();

    // 마지막으로 추론을 수행한 시각 (밀리초)
    private long lastInferenceTime = 0;

    public enum Direction { LEFT_UP, UP, RIGHT_UP, LEFT, RIGHT, LEFT_DOWN, DOWN, RIGHT_DOWN, BLINK }

    private static final float[] FEATURE_MEANS = new float[]{
            322.158780f, -325.076977f, 126.873161f, 0.043643f, 1.862295f, 3.890496f,
            364.921066f, -385.631221f, 159.955604f, -0.160811f, 0.760025f, 22.196281f
    };

    private static final float[] FEATURE_STDS = new float[]{
            172.854051f, 195.419945f, 58.375951f, 1.274993f, 1.365249f, 13.432352f,
            280.790807f, 255.677729f, 123.099283f, 0.927015f, 3.265303f, 37.137848f
    };

    public interface EOGEventListener {
        void onEyeMovement(Direction direction);
        void onRawData(String rawData);
    }

    public EOGManager(Context context) {
        this.context = context;
        bluetoothHelper = new BluetoothHelper(context);
        bluetoothHelper.setConnectionListener(new BluetoothHelper.ConnectionListener() {
            @Override
            public void onConnected() {}

            @Override
            public void onConnectionFailed(String reason) {
            }

            @Override
            public void onDataReceived(String data) {
                if (eogEventListener != null) eogEventListener.onRawData(data);
                processSensorData(data);
            }
        });

        // ANN classifier initialize
        try {
            // assets/model_traced_84.pt를 내부 저장소로 복사
            String modelFilePath = copyAssetToDisk(context.getAssets(), "model_traced_84z.pt");
            module = Module.load(modelFilePath);
            Log.d(TAG, "PyTorch module loaded successfully from: " + modelFilePath);
        } catch (IOException e) {
            Log.e(TAG, "Failed to copy model asset to disk", e);
            module = null;
        } catch (Exception e) {
            Log.e(TAG, "Failed to load PyTorch module", e);
            module = null;
        }

        testInferenceWithDummyData();
    }

    public void testInferenceWithDummyData() {
        Log.d("EOGManagerTest", "Model test start");

        String assetFileName = "test_data.txt";
        try {
            // 2) AssetManager를 통해 텍스트 파일 열기
            InputStream is = context.getAssets().open(assetFileName);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));

            // 3) 기존 buffer 초기화
            buffer.clear();

            String line;
            long baseTime = System.currentTimeMillis();
            int idx = 0;

            // 4) 파일의 모든 줄을 읽어와서 파싱
            while ((line = reader.readLine()) != null) {
                // 예시 라인 형식: "[4] [52:53.888] ,X:2399,Y:1514,Z:0"
                // 쉼표(,)로 split하면 parts[0] = "[4] [52:53.888] ",
                //                       parts[1] = "X:2399"
                //                       parts[2] = "Y:1514"
                //                       parts[3] = "Z:0"
                String[] parts = line.split(",");

                if (parts.length < 3) {
                    Log.d("EOGManagerTest", " 테스트 파싱 결과 형식이 올바르지 않습니다.");
                    continue;
                }

                // parts[1]에서 X 값을 파싱
                String xPart = parts[1].trim(); // "X:2399"
                float x = Float.parseFloat(xPart.split(":")[1]);

                // parts[2]에서 Y 값을 파싱
                String yPart = parts[2].trim(); // "Y:1514"
                float y = Float.parseFloat(yPart.split(":")[1]);

                // (Optional) Z 값은 parts[3]에 있지만, 현재 preprocess 단계에서는 사용하지 않으므로 무시해도 됩니다.
                // String zPart = parts[3].trim(); // "Z:0"
                // float z = Float.parseFloat(zPart.split(":")[1]);

                // 5) 임시 timestamp 생성 (baseTime + idx). 실제 시간 정보를 쓰고 싶다면
                //    "[52:53.888]" 형태를 파싱해서 ms 단위로 변환하여 사용할 수 있습니다.
                long timestamp = baseTime + idx;
                idx++;

                // 6) Sample 객체로 생성하여 buffer에 추가
                Sample sample = new Sample(timestamp, x, y);
                buffer.addLast(sample);
            }

            reader.close();

            Sample s = buffer.getFirst();
            Log.d("EOGManagerTest", "Buffer get first");
            Log.d("EOGManagerTest", "x: " + s.x);
            Log.d("EOGManagerTest", "y: " + s.y);
            Log.d("EOGManagerTest", "timestamp: " + s.timestamp);

            // 7) buffer.DATA를 기반으로 preprocessBufferData() 호출하여 14차원 피처 생성
            float[] inputData = preprocessBufferData();

            Log.d("EOGManagerTest", "Processed Data");
            if (inputData != null) {
                for (float item: inputData) {
                    Log.d("EOGManagerTest", "" + item);
                }
            }

            if (inputData.length == 12) {
                int predictedLabel = runInference(inputData);
                Log.d("EOGManagerTest", "File-based input inference result = " + predictedLabel);
            } else {
                Log.d("EOGManagerTest", "파일 데이터로부터 충분한 피처를 생성하지 못했습니다.");
            }

        } catch (IOException e) {
            Log.e("EOGManagerTest", "testInferenceWithDummyData: 파일을 읽는 중 오류 발생", e);
        }
    }

    /**
     * assets 내부의 모델 파일을 앱 내부 저장소로 복사
     */
    private String copyAssetToDisk(AssetManager assetManager, String assetName) throws IOException {
        // assets 폴더 리스트 출력 (디버깅용)
        try {
            String[] assetList = assetManager.list("");
            Log.d(TAG, "AssetManager.list(\"\") => count=" + assetList.length);
            for (String s : assetList) {
                Log.d(TAG, "  - asset: " + s);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to list assets", e);
        }

        File cacheFile = new File(context.getFilesDir(), assetName);
        if (!cacheFile.exists()) {
            InputStream inputStream = null;
            FileOutputStream outputStream = null;
            try {
                // *** openFd 대신 open 사용 ***
                inputStream = assetManager.open(assetName);
                outputStream = new FileOutputStream(cacheFile);
                byte[] buffer = new byte[8192];
                int readBytes;
                while ((readBytes = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, readBytes);
                }
                outputStream.flush();
                Log.d(TAG, "copyAssetToDisk: asset 복사 완료 -> " + cacheFile.getAbsolutePath());
            } catch (IOException openEx) {
                Log.e(TAG, "copyAssetToDisk: 파일을 열 수 없습니다. assetName=" + assetName, openEx);
                throw openEx;
            } finally {
                if (inputStream != null) {
                    try { inputStream.close(); } catch (IOException ignored) {}
                }
                if (outputStream != null) {
                    try { outputStream.close(); } catch (IOException ignored) {}
                }
            }
        } else {
            Log.d(TAG, "copyAssetToDisk: 이미 복사되어 있음 -> " + cacheFile.getAbsolutePath());
        }
        return cacheFile.getAbsolutePath();
    }

    /**
     * 입력 배열(inputData)을 Tensor로 변환 후 모델에 전달하여 예측 라벨 반환
     */
    private int runInference(float[] inputData) {
        final long[] inputShape = new long[]{1, inputData.length};
        Tensor inputTensor = Tensor.fromBlob(inputData, inputShape);
        IValue outputIValue = module.forward(IValue.from(inputTensor));
        Tensor outputTensor = outputIValue.toTensor();

        float[] scores = outputTensor.getDataAsFloatArray();
        int maxIdx = 0;
        float maxScore = scores[0];

        Log.d(TAG, "Inference score");
        for (int i = 0; i < scores.length; i++) {
            Log.d(TAG, i + ": " + scores[i]);

            if (scores[i] > maxScore) {
                maxScore = scores[i];
                maxIdx = i;
            }
        }
        return maxIdx;
    }

    public void setEOGEventListener(EOGEventListener listener) {
        this.eogEventListener = listener;
    }

    public void connect(String deviceName, UUID uuid) {
        bluetoothHelper.connect(deviceName, uuid);
    }

    public void disconnect() {
        bluetoothHelper.disconnect();
    }

    private void processSensorData(String data) {
        String[] temp = data.split(",");
        String[] temp2 = temp[0].split(":");
        float x = Float.parseFloat(temp2[1]);
        temp2 = temp[1].split(":");
        float y = Float.parseFloat(temp2[1]);

        long currentTime = System.currentTimeMillis();

        // 새 샘플을 버퍼에 추가
        Sample sample = new Sample(currentTime, x, y);
        buffer.addLast(sample);

        // 버퍼에서 1.5초 이전 샘플 제거 (1500ms)
        while (!buffer.isEmpty() && (currentTime - buffer.peekFirst().timestamp) > 1500) {
            buffer.removeFirst();
        }

        // 마지막 추론 시점으로부터 0.2초(200ms) 이상 지났으면 추론 실행
        if (currentTime - lastInferenceTime >= 200) {
            // lastInferenceTime을 현재 시점 +1초로 설정
            // 1초 동안은 신호 판단 x
            lastInferenceTime = currentTime + 10000;

            // 버퍼 데이터를 가공하여 모델 입력용 배열 생성
            float[] inputData = preprocessBufferData();

            if (inputData != null && inputData.length > 0) {
                int resultIdx = runInference(inputData);
                Direction direction = Direction.values()[resultIdx];
                if (eogEventListener != null) {
                    eogEventListener.onEyeMovement(direction);
                }
            }
        }
    }

    /**
     * 버퍼에 담긴 샘플들을 가공하여 모델이 요구하는 14차원 입력 형태의 float[]를 반환
     * 1) x, y 데이터 각각에 대해 detrend 적용
     * 2) x, y 각각 7개 피쳐 추출: [max, min, mean, std, skewness, kurtosis, dominant frequency]
     * 3) Python StandardScaler와 동일하게 정규화 적용
     */
    private float[] preprocessBufferData() {
        int N = buffer.size();
        if (N < 2) {
            return null; // 샘플이 충분하지 않으면 null 반환
        }

        // 버퍼 내용을 배열로 복사 (오래된 순서대로)
        float[] xVals = new float[N];
        float[] yVals = new float[N];
        int idx = 0;
        Iterator<Sample> it = buffer.iterator();
        while (it.hasNext()) {
            Sample s = it.next();
            xVals[idx] = s.x;
            yVals[idx] = s.y;
            idx++;
        }

        // 1) detrend
        float[] xDet = detrendArray(xVals);
        float[] yDet = detrendArray(yVals);

        // 2) xDet, yDet 각각의 min/max를 구함
        float xMaxRaw = xDet[0], xMinRaw = xDet[0];
        float yMaxRaw = yDet[0], yMinRaw = yDet[0];
        for (int i = 1; i < N; i++) {
            if (xDet[i] > xMaxRaw) xMaxRaw = xDet[i];
            if (xDet[i] < xMinRaw) xMinRaw = xDet[i];
            if (yDet[i] > yMaxRaw) yMaxRaw = yDet[i];
            if (yDet[i] < yMinRaw) yMinRaw = yDet[i];
        }

        // 3) x,y 의 min/max 절대값 중 하나라도 400 초과하지 않으면 null 반환
        if (Math.abs(xMaxRaw) <= 400.0f &&
                Math.abs(xMinRaw) <= 400.0f &&
                Math.abs(yMaxRaw) <= 400.0f &&
                Math.abs(yMinRaw) <= 400.0f) {
            return null;
        }

        // 4) 피쳐 추출
        float[] xFeatures = computeFeatures(xDet);
        float[] yFeatures = computeFeatures(yDet);

        // 5) 14차원 결과 벡터 생성
        float[] features = new float[12];
        System.arraycopy(xFeatures, 0, features, 0, 6);
        System.arraycopy(yFeatures, 0, features, 6, 6);

        // 6) 정규화 (StandardScaler 방식, STD가 0인 경우 0으로 처리)
        for (int i = 0; i < features.length; i++) {
            float mean = FEATURE_MEANS[i];
            float std  = FEATURE_STDS[i];
            features[i] = (features[i] - mean) / std;
        }

        return features;
    }

    /**
     * 배열 arr에 대해 선형 추세(linear trend)를 제거한 결과 반환
     * (최소제곱법으로 추세선을 구하여 빼줌)
     */
    private float[] detrendArray(float[] arr) {
        int N = arr.length;
        if (N < 2) {
            return arr.clone();
        }

        // Σi, Σi^2, Σy, Σ(i*y)
        double sumI = 0.0;
        double sumII = 0.0;
        double sumY = 0.0;
        double sumIY = 0.0;
        for (int i = 0; i < N; i++) {
            sumI += i;
            sumII += i * i;
            sumY += arr[i];
            sumIY += i * arr[i];
        }

        double n = N;
        double denominator = (n * sumII - sumI * sumI);
        double slope = 0.0;
        if (denominator != 0.0) {
            slope = (n * sumIY - sumI * sumY) / denominator;
        }
        double intercept = (sumY - slope * sumI) / n;

        float[] detrended = new float[N];
        for (int i = 0; i < N; i++) {
            double trend = slope * i + intercept;
            detrended[i] = (float)(arr[i] - trend);
        }
        return detrended;
    }

    /**
     * 단일 배열 arr에 대해 6개 피쳐를 계산하여 float[6]로 반환
     * [max, min, mean, std, skewness, kurtosis, dominant frequency index]
     */
    private float[] computeFeatures(float[] arr) {
        int N = arr.length;

        // 1) max, min
        float maxV = arr[0];
        float minV = arr[0];
        for (int i = 0; i < N; i++) {
            if (arr[i] > maxV) maxV = arr[i];
            if (arr[i] < minV) minV = arr[i];
        }

        // 2) std
        double sumSq = 0.0;
        for (int i = 0; i < N; i++) {
            double diff = arr[i];
            sumSq += diff * diff;
        }
        double variance = sumSq / N;
        double std = Math.sqrt(variance);

        // 3) skewness, kurtosis
        double sumCubed = 0.0;
        double sumFourth = 0.0;
        if (std > 0.0) {
            for (int i = 0; i < N; i++) {
                double norm = (arr[i]) / std;
                sumCubed += norm * norm * norm;
                sumFourth += norm * norm * norm * norm;
            }
            sumCubed /= N;
            sumFourth = sumFourth / N - 3.0; // excess kurtosis
        } else {
            sumCubed = 0.0;
            sumFourth = 0.0;
        }
        float skewness = (float) sumCubed;
        float kurtosis = (float) sumFourth;

        // 4) dominant frequency index (naive DFT)
        int domFreq = computeDominantFrequency(arr);

        return new float[]{
                maxV,
                minV,
                (float)std,
                skewness,
                kurtosis,
                (float)domFreq
        };
    }

    /**
     * 배열 arr에 대해 1차원 DFT를 수행하여 dominant frequency bin index 반환
     * 0번(DC)을 제외한 인덱스 중 magnitude가 가장 큰 것.
     */
    private int computeDominantFrequency(float[] arr) {
        int N = arr.length;
        if (N < 2) {
            return 0;
        }

        int half = N / 2;
        double twoPiOverN = 2.0 * Math.PI / N;
        int domIndex = 0;
        double maxMag = -1.0;

        // k = 1 부터 half까지 계산 (DC 제외)
        for (int k = 1; k <= half; k++) {
            double real = 0.0;
            double imag = 0.0;
            for (int n = 0; n < N; n++) {
                double angle = twoPiOverN * k * n;
                real += arr[n] * Math.cos(angle);
                imag -= arr[n] * Math.sin(angle);
            }
            double mag = Math.hypot(real, imag);
            if (mag > maxMag) {
                maxMag = mag;
                domIndex = k;
            }
        }
        return domIndex;
    }
}