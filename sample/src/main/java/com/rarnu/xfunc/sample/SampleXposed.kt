package com.rarnu.xfunc.sample

import com.rarnu.xfunc.*

class SampleXposed: XposedPackage() {
    override fun hook(pkg: XposedPkg) {
        if (pkg.packageName == "com.miui.core") {
            pkg.findClass("miui.os.SystemProperties").findMethod("get", String::class.java, String::class.java).hook {
                after {
                    if (args[0] == "ro.product.mod_device") {
                        result = "gemini_global"
                    }
                }
            }
            pkg.findClass("miui.os.SystemProperties.Build").findField("IS_CM_CUSTOMIZATION_TEST").setStatic(true)
            pkg.findClass("com.miui.internal.util").findField("IS_INTERNAL_BUILD").setStatic(true)
        }
    }
}

