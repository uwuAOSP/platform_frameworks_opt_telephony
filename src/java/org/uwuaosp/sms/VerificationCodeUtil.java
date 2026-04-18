/*
 * Copyright (C) 2025-2026 The AviumUI Project
 * Copyright (C) 2026 The uwuAOSP Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.uwuaosp.sms;

import android.content.Context;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.SmsMessage;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class VerificationCodeUtil {
    public static final String ACTION_SMS_CODE_RECEIVED =
            "org.uwuaosp.systemui.action.SMS_CODE_RECEIVED";
    public static final String EXTRA_CODE = "code";
    public static final String SYSTEMUI_PACKAGE = "com.android.systemui";

    private static final String SECURE_KEY_RULES = "uwuaosp_sms_code_rules";
    private static final String SECURE_KEY_ENABLED = "uwuaosp_sms_code_suggestion_enabled";

    private static final String JSON_PATTERN = "pattern";
    private static final String JSON_ENABLED = "enabled";

    private VerificationCodeUtil() {
    }

    public static boolean isSuggestionEnabled(Context context) {
        return Settings.Secure.getIntForUser(
                context.getContentResolver(),
                SECURE_KEY_ENABLED,
                0,
                UserHandle.USER_CURRENT) == 1;
    }

    public static String extractVerificationCode(Context context, byte[][] pdus, String format) {
        if (pdus == null || pdus.length == 0) {
            return null;
        }

        StringBuilder messageBody = new StringBuilder();
        for (byte[] pdu : pdus) {
            SmsMessage message = SmsMessage.createFromPdu(pdu, format);
            if (message != null && message.getMessageBody() != null) {
                messageBody.append(message.getMessageBody());
            }
        }
        return extractVerificationCode(context, messageBody.toString());
    }

    public static String extractVerificationCode(Context context, String messageBody) {
        if (TextUtils.isEmpty(messageBody)) {
            return null;
        }

        List<String> patterns = loadEnabledPatterns(context);
        for (String rule : patterns) {
            try {
                Matcher matcher = Pattern.compile(
                        rule, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL)
                        .matcher(messageBody);
                if (!matcher.find()) {
                    continue;
                }
                String candidate = extractMatchedValue(matcher);
                if (!TextUtils.isEmpty(candidate)) {
                    return candidate.trim();
                }
            } catch (PatternSyntaxException ignored) {
                // Ignore invalid user rules.
            }
        }

        return null;
    }

    private static List<String> loadEnabledPatterns(Context context) {
        String json = Settings.Secure.getStringForUser(
                context.getContentResolver(),
                SECURE_KEY_RULES,
                UserHandle.USER_CURRENT);
        ArrayList<String> patterns = new ArrayList<>();
        if (TextUtils.isEmpty(json)) {
            return patterns;
        }

        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null || !object.optBoolean(JSON_ENABLED, true)) {
                    continue;
                }
                String pattern = object.optString(JSON_PATTERN);
                if (!TextUtils.isEmpty(pattern)) {
                    patterns.add(pattern);
                }
            }
        } catch (Exception ignored) {
            patterns.clear();
        }
        return patterns;
    }

    private static String extractMatchedValue(Matcher matcher) {
        for (int i = 1; i <= matcher.groupCount(); i++) {
            String group = matcher.group(i);
            if (!TextUtils.isEmpty(group)) {
                return group;
            }
        }
        return matcher.group();
    }
}
