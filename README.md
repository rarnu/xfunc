# xfunc

一个方便 Xposed 开发的函数库

- - -

在 gradle 内使用:

```gradle
compileOnly 'de.robv.android.xposed:api:82'
implementation "com.github.rarnu:xfunc:0.3.0"
implementation "com.github.rarnu:ktfunctional:0.8.3"
```

- - -

可以将原本的 Xposed 代码精简为更易读，易维护的形式，例如以下原始代码：

```kotlin
class SampleXposed: IXposedHookLoadPackage {
    override fun handleLoadPackage(paramLoadPackageParam: XC_LoadPackage.LoadPackageParam) {
        if (paramLoadPackageParam.packageName == "com.miui.core") {
            XpUtils.findAndHookMethod(
                "miui.os.SystemProperties", paramLoadPackageParam.classLoader, 
                "get", String::class.java, String::class.java, 
                object : XC_MethodHook() {
                    override fun afterHookedMethod(paramAnonymousMethodHookParam: XC_MethodHook.MethodHookParam) {
                        if (paramAnonymousMethodHookParam.args[0].toString() == "ro.product.mod_device") {
                            paramAnonymousMethodHookParam.result = "gemini_global"
                        }
                    }
                })
            XposedHelpers.setStaticBooleanField(Class.forName("miui.os.SystemProperties.Build"), "IS_CM_CUSTOMIZATION_TEST", true)
            XposedHelpers.setStaticBooleanField(Class.forName("com.miui.internal.util"), "IS_INTERNATIONAL_BUILD", true)
        }
    }
}
```

可以被以下形式：

```kotlin
class SampleXposed: XposedPackage() {
    override fun hook(pkg: XposedPkg) {
        if (pkg.packageName == "com.miui.core") {
            pkg.findClass("miui.os.SystemProperties")
                .findMethod("get", String::class.java, String::class.java)
                .hook {
                    after {
                        if (args[0] == "ro.product.mod_device") {
                            result = "gemini_global"
                        }
                    }
                }

            pkg.findClass("miui.os.SystemProperties.Build")
                .findField("IS_CM_CUSTOMIZATION_TEST")
                .setStatic(true)
            pkg.findClass("com.miui.internal.util")
                .findField("IS_INTERNAL_BUILD")
                .setStatic(true)
        }
    }
}


```

- - -

其余功能请自行查阅代码。
