# 📱 المجهر الطبي — تعليمات البناء

## للحصول على APK:
1. افتح تبويب **Actions** في هذا المستودع
2. انتظر انتهاء "Build APK" (يصبح أخضر ✅)
3. اضغط عليه → انزل لقسم **Artifacts** → حمّل `MicroscopeApp-APK`
4. فك الضغط → ثبّت `app-debug.apk` على الهاتف

## إذا لم يعمل البناء تلقائياً:
تأكد من وجود الملف `.github/workflows/build.yml`
(محتواه مخزون في ملف WORKFLOW_BACKUP_README.md هذا المجلد
 أو أعِد إنشائه: Add file → Create new file → اكتب المسار أعلاه)

## البناء محلياً:
./gradlew assembleDebug
