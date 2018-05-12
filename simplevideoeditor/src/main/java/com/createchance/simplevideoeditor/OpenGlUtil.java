package com.createchance.simplevideoeditor;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Map;
import java.util.Set;

import static android.opengl.GLES20.GL_COMPILE_STATUS;
import static android.opengl.GLES20.GL_LINEAR;
import static android.opengl.GLES20.GL_LINEAR_MIPMAP_LINEAR;
import static android.opengl.GLES20.GL_LINK_STATUS;
import static android.opengl.GLES20.GL_NO_ERROR;
import static android.opengl.GLES20.GL_TEXTURE_2D;
import static android.opengl.GLES20.GL_TEXTURE_MAG_FILTER;
import static android.opengl.GLES20.GL_TEXTURE_MIN_FILTER;
import static android.opengl.GLES20.GL_VALIDATE_STATUS;
import static android.opengl.GLES20.glAttachShader;
import static android.opengl.GLES20.glBindTexture;
import static android.opengl.GLES20.glCompileShader;
import static android.opengl.GLES20.glCreateProgram;
import static android.opengl.GLES20.glCreateShader;
import static android.opengl.GLES20.glDeleteProgram;
import static android.opengl.GLES20.glDeleteShader;
import static android.opengl.GLES20.glGenTextures;
import static android.opengl.GLES20.glGenerateMipmap;
import static android.opengl.GLES20.glGetAttribLocation;
import static android.opengl.GLES20.glGetError;
import static android.opengl.GLES20.glGetProgramInfoLog;
import static android.opengl.GLES20.glGetProgramiv;
import static android.opengl.GLES20.glGetShaderInfoLog;
import static android.opengl.GLES20.glGetShaderiv;
import static android.opengl.GLES20.glGetUniformLocation;
import static android.opengl.GLES20.glLinkProgram;
import static android.opengl.GLES20.glShaderSource;
import static android.opengl.GLES20.glTexParameteri;
import static android.opengl.GLES20.glValidateProgram;

/**
 * ${DESC}
 *
 * @author gaochao1-iri
 * @date 23/04/2018
 */
class OpenGlUtil {
    private static final String TAG = "OpenGlUtil";

    public static boolean DEBUG = true;

    public static FloatBuffer getFloatBuffer(float[] data, int bytesPerFloat) {
        return ByteBuffer
                .allocateDirect(data.length * bytesPerFloat)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(data);
    }

    public static int loadShaderFromRes(Context context, int resId, int shaderType) {
        return compileShader(shaderType, readShaderTextFromRes(context, resId));
    }

    public static int loadShaderFromFile(File shaderFile, int shaderType) {
        return compileShader(shaderType, readShaderTextFromFile(shaderFile));
    }

    public static int buildProgram(int vertexShaderId, int fragmentShaderId) {
        int programObjectId = glCreateProgram();

        if (programObjectId == 0) {
            Log.e(TAG, "linkProgram, error");
        } else {
            glAttachShader(programObjectId, vertexShaderId);
            glAttachShader(programObjectId, fragmentShaderId);
            glLinkProgram(programObjectId);
            Log.d(TAG, "linkProgram, linking program, result: " + glGetProgramInfoLog(programObjectId));
            int[] linkStatus = new int[1];
            glGetProgramiv(programObjectId, GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] == 0) {
                glDeleteProgram(programObjectId);
                Log.d(TAG, "linkProgram failed.");
            }
        }

        return programObjectId;
    }

    public static boolean validateProgram(int programObjectId) {
        glValidateProgram(programObjectId);

        int[] validateStatus = new int[1];
        glGetProgramiv(programObjectId, GL_VALIDATE_STATUS, validateStatus, 0);
        if (DEBUG) {
            Log.d(TAG, "validateProgram, validating program, result: " + validateStatus[0]
                    + ", log: " + glGetProgramInfoLog(programObjectId));
        }

        return validateStatus[0] != 0;
    }

    public static void getShaderParams(int programId, Map<String, ShaderParam> shaderParamMap) {
        Set<String> keySet = shaderParamMap.keySet();
        for (String key : keySet) {
            ShaderParam param = shaderParamMap.get(key);
            switch (param.type) {
                case ShaderParam.TYPE_ATTRIBUTE:
                    param.location = glGetAttribLocation(programId, param.name);
                    break;
                case ShaderParam.TYPE_UNIFORM:
                    param.location = glGetUniformLocation(programId, param.name);
                    break;
                default:
                    break;
            }
        }
    }

    public static int loadTextureFromRes(Context context, int resId) {
        return createAndBindTexture(decodeBitmapFromRes(context, resId));
    }

    public static int loadTextureFromFile(File imgFile) {
        return createAndBindTexture(decodeBitmapFromFile(imgFile));
    }

    public static void perspectiveM(float[] m, float yFovInDegrees, float aspect,
                                    float n, float f) {
        final float angleInRadians = (float) (yFovInDegrees * Math.PI / 180.0);

        final float a = (float) (1.0 / Math.tan(angleInRadians / 2.0));
        m[0] = a / aspect;
        m[1] = 0f;
        m[2] = 0f;
        m[3] = 0f;

        m[4] = 0f;
        m[5] = a;
        m[6] = 0f;
        m[7] = 0f;

        m[8] = 0f;
        m[9] = 0f;
        m[10] = -((f + n) / (f - n));
        m[11] = -1f;

        m[12] = 0f;
        m[13] = 0f;
        m[14] = -((2f * f * n) / (f - n));
        m[15] = 0f;
    }

    public static void assertNoError(String op) {
        int error;
        if ((error = glGetError()) != GL_NO_ERROR) {
            Log.e(TAG, String.format(op + ": glError 0x%x", error));
            throw new RuntimeException("String.format(op + \": glError 0x%x\", error)");
        }
    }

    private static Bitmap decodeBitmapFromRes(Context context, int resId) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        // 我们想要图片的原始数据而不是缩放之后的版本
        options.inScaled = false;
        return BitmapFactory.decodeResource(context.getResources(), resId, options);
    }

    private static Bitmap decodeBitmapFromFile(File imgFile) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        // 我们想要图片的原始数据而不是缩放之后的版本
        options.inScaled = false;
        return BitmapFactory.decodeFile(imgFile.getAbsolutePath(), options);
    }

    private static int createAndBindTexture(Bitmap texture) {
        if (texture == null) {
            return 0;
        }

        int[] textureObjectIds = new int[1];
        glGenTextures(1, textureObjectIds, 0);

        if (textureObjectIds[0] == 0) {
            return 0;
        }

        glBindTexture(GL_TEXTURE_2D, textureObjectIds[0]);

        // 纹理过滤
        // 纹理缩小的时候使用三线性过滤
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        // 纹理放大的时候使用双线性过滤
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

        // 加载位图到opengl中
        GLUtils.texImage2D(GL_TEXTURE_2D, 0, texture, 0);
        texture.recycle();

        // 生成mip贴图
        glGenerateMipmap(GL_TEXTURE_2D);

        // 既然我们已经完成了纹理的加载，现在需要和纹理解除绑定
        glBindTexture(GL_TEXTURE_2D, 0);

        return textureObjectIds[0];
    }

    private static String readShaderTextFromRes(Context context, int resId) {
        StringBuilder text = new StringBuilder();

        InputStream inputStream = context.getResources().openRawResource(resId);
        InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
        String line;

        try {
            while ((line = bufferedReader.readLine()) != null) {
                text.append(line);
                text.append('\n');
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Resources.NotFoundException e) {
            e.printStackTrace();
        }

        return text.toString();
    }

    private static String readShaderTextFromFile(File shaderFile) {
        StringBuilder text = new StringBuilder();
        try {
            InputStream inputStream = new FileInputStream(shaderFile);
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String line;

            while ((line = bufferedReader.readLine()) != null) {
                text.append(line);
                text.append('\n');
            }
        } catch (IOException e) {
            if (DEBUG) {
                e.printStackTrace();
            }
        }

        return text.toString();
    }

    private static int compileShader(int shaderType, String shaderSource) {
        int shaderObjectId = glCreateShader(shaderType);
        if (shaderObjectId == 0) {
            Log.e(TAG, "compileShader error! Shader type: " + shaderType);
            Log.e(TAG, "Shader source: " + shaderSource);
        } else {
            glShaderSource(shaderObjectId, shaderSource);
            glCompileShader(shaderObjectId);
            int[] compileStatus = new int[1];
            glGetShaderiv(shaderObjectId, GL_COMPILE_STATUS, compileStatus, 0);
            Log.d(TAG, "onSurfaceCreated, compiling vertex source, result: " + glGetShaderInfoLog(shaderObjectId));
            if (compileStatus[0] == 0) {
                glDeleteShader(shaderObjectId);
                shaderObjectId = 0;
                Log.d(TAG, "onSurfaceCreated, compile vertex failed.");
            }
        }

        return shaderObjectId;
    }

    public static class ShaderParam {
        public static final int TYPE_ATTRIBUTE = 1;
        public static final int TYPE_UNIFORM = 2;

        public int type;
        public String name;
        public int location;

        public ShaderParam(int type, String name) {
            this.type = type;
            this.name = name;
        }

        @Override
        public String toString() {
            return "ShaderParam{" +
                    "type=" + type +
                    ", name='" + name + '\'' +
                    ", location=" + location +
                    '}';
        }
    }
}
