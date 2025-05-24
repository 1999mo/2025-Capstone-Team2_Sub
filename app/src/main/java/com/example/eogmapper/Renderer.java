package com.example.eogmapper;

import android.app.Activity;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import java.util.Random;
import android.util.Log;

import com.example.eogmodule.EOGManager;

public class Renderer implements GLSurfaceView.Renderer {
    private static final String vertexShaderCode =
            "attribute vec4 a_Position;\n" +
                    "attribute vec2 a_TexCoord;\n" +
                    "varying vec2 v_TexCoord;\n" +
                    "void main() {\n" +
                    "    gl_Position = a_Position;\n" +
                    "    v_TexCoord = a_TexCoord;\n" +
                    "}";

    private static final String fragmentShaderCode =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;\n" +
                    "uniform sampler2D u_Texture;\n" +
                    "uniform vec4 u_Color;\n" +
                    "uniform bool u_UseTexture;\n" +
                    "varying vec2 v_TexCoord;\n" +
                    "vec2 barrelDistortion(vec2 uv, float k) {\n" +
                    "    vec2 center = vec2(0.5, 0.5);\n" +
                    "    vec2 delta = uv - center;\n" +
                    "    float r2 = dot(delta, delta);\n" +
                    "    return center + delta * (1.0 + k * r2);\n" +
                    "}\n" +
                    "void main() {\n" +
                    "    vec2 distortedUV = barrelDistortion(v_TexCoord, -0.3);\n" +
                    "    if (distortedUV.x < 0.0 || distortedUV.x > 1.0 || distortedUV.y < 0.0 || distortedUV.y > 1.0) {\n" +
                    "        discard;\n" +
                    "    }\n" +
                    "    if (u_UseTexture) {\n" +
                    "        gl_FragColor = texture2D(u_Texture, distortedUV);\n" +
                    "    } else {\n" +
                    "        gl_FragColor = u_Color;\n" +
                    "    }\n" +
                    "}";

    private FloatBuffer vertexBuffer, texCoordBuffer;
    private int shaderProgram;
    private int textureId;
    private int screenWidth;
    private int screenHeight;

    private final float[] squareCoords = {
            -1.0f, 1.0f,
            -1.0f, -1.0f,
            1.0f, 1.0f,
            1.0f, -1.0f
    };

    private final float[] texCoords = {
            0.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 0.0f,
            1.0f, 1.0f
    };

    private static final int TOTAL_SECTIONS = 8;
    private int highlightedIndex = -1;
    private int prevIndex = -1;
    private int iteration = 0;
    private static final int TOTAL_ITER = 20;

    private float[][] sectionColors = new float[TOTAL_SECTIONS][4]; // RGBA
    private float[][] sectionRects = new float[TOTAL_SECTIONS][8]; // 4 vertices (x,y) * 4

    private Handler handler = new Handler(Looper.getMainLooper());
    private Random random = new Random();
    private final EOGManager eogManager;

    public Renderer(EOGManager eogManager) {
        this.eogManager = eogManager;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        Log.d("Renderer", "Start of surface creation");
        //setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        GLES20.glClearColor(0f, 0f, 0f, 0f);
        vertexBuffer = ByteBuffer.allocateDirect(squareCoords.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(squareCoords);
        vertexBuffer.position(0);

        texCoordBuffer = ByteBuffer.allocateDirect(texCoords.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(texCoords);
        texCoordBuffer.position(0);

        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);

        shaderProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(shaderProgram, vertexShader);
        GLES20.glAttachShader(shaderProgram, fragmentShader);
        GLES20.glLinkProgram(shaderProgram);

        textureId = 0;

        for (int i = 0; i < TOTAL_SECTIONS; i++) {
            sectionColors[i] = new float[]{0.8f, 0.8f, 0.8f, 1.0f}; // 기본색 (회색)
            sectionRects[i] = computeRect(i);
        }
        scheduleNextHighlight();

        eogManager.setEOGEventListener(rawData -> writeLog(rawData + "\n"));
        Log.d("Renderer", "End of surface creation");
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        Log.d("Renderer", "Start of surface change");
        screenWidth = width;
        screenHeight = height;
        Log.d("Renderer", "End of surface change");
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        Log.d("Renderer", "Start of drawframe");
        int eyeWidth = (int) (screenWidth * 0.46);
        int eyeGap = (int) (screenWidth * 0.04);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glUseProgram(shaderProgram);

        int positionHandle = GLES20.glGetAttribLocation(shaderProgram, "a_Position");
        int texCoordHandle = GLES20.glGetAttribLocation(shaderProgram, "a_TexCoord");
        int textureHandle = GLES20.glGetUniformLocation(shaderProgram, "u_Texture");
        int useTextureHandle = GLES20.glGetUniformLocation(shaderProgram, "u_UseTexture");;

        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);

        GLES20.glEnableVertexAttribArray(texCoordHandle);
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer);

        GLES20.glUniform1i(useTextureHandle, 1);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        GLES20.glUniform1i(textureHandle, 0);

        // 왼쪽 눈
        GLES20.glViewport(eyeGap, 0, eyeWidth, screenHeight);
        drawScene();

        // 오른쪽 눈
        GLES20.glViewport(screenWidth - eyeWidth - eyeGap, 0, eyeWidth, screenHeight);
        drawScene();

        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(texCoordHandle);
        Log.d("Renderer", "End of drawframe");
    }
    private int loadShader(int type, String shaderCode) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        return shader;
    }

    private int createExternalTexture() {
        int[] texture = new int[1];
        GLES20.glGenTextures(1, texture, 0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture[0]);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        return texture[0];
    }

    private void drawScene() {
        for (int i = 0; i < TOTAL_SECTIONS; i++) {
            drawRect(sectionRects[i], sectionColors[i]);
        }
    }

    private void drawRect(float[] rect, float[] color) {
        Log.d("Renderer", "drawRect called");
        Log.d("Renderer", "Rect: [" + rect[0] + ", " + rect[1] + ", " + rect[2] + ", " + rect[3] + "]");
        Log.d("Renderer", "Color: [" + color[0] + ", " + color[1] + ", " + color[2] + ", " + color[3] + "]");

        FloatBuffer rectBuffer = ByteBuffer.allocateDirect(rect.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        rectBuffer.put(rect).position(0);

        int positionHandle = GLES20.glGetAttribLocation(shaderProgram, "a_Position");
        int colorHandle = GLES20.glGetUniformLocation(shaderProgram, "u_Color");
        int useTextureHandle = GLES20.glGetUniformLocation(shaderProgram, "u_UseTexture");

        GLES20.glUseProgram(shaderProgram);
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, rectBuffer);

        GLES20.glUniform1i(useTextureHandle, 0);
        GLES20.glUniform4fv(colorHandle, 1, color, 0);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(positionHandle);
    }

    private float[] computeRect(int idx) {
        int row = idx / 4;
        int col = idx % 4;

        float w = 2f / 4f;
        float h = 2f / 2f;

        float left = -1f + col * w;
        float top = 1f - row * h;

        return new float[]{
                left, top,
                left, top - h,
                left + w, top,
                left + w, top - h
        };
    }

    private void scheduleNextHighlight() {
        handler.postDelayed(() -> {
            if (iteration >= TOTAL_ITER) return;

            int idx;
            do {
                idx = random.nextInt(TOTAL_SECTIONS);
            } while (idx == prevIndex);
            prevIndex = idx;

            for (int i = 0; i < TOTAL_SECTIONS; i++) {
                sectionColors[i] = new float[]{0.8f, 0.8f, 0.8f, 1.0f};
            }

            sectionColors[idx] = new float[]{0.71f, 0.84f, 0.57f, 1.0f}; // 연두색

            iteration++;
            scheduleNextHighlight();
        }, 1500);
    }

    private void writeLog(String log) {
        try {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File logFile = new File(downloadsDir, "EOG_log.txt");

            FileWriter writer = new FileWriter(logFile, true); // true로 하면 append(추가) 모드
            writer.append(log);
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
