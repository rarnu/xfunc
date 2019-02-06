@file:Suppress("Duplicates", "MemberVisibilityCanBePrivate", "HasPlatformType", "unused")

package com.rarnu.xfunc

import android.content.Intent
import android.os.Bundle
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers

object Hook {

    /**
     * 从类实例中查找指定类型的实例
     * @param aobj 类实例
     * @param atype 要查找的类型
     * @param additionalCondition 附加条件
     * @return 命中的类内实例
     */
    fun hitAny(aobj: Any, atype: Class<*>, additionalCondition: (Any) -> Boolean): Any? {
        var ret: Any? = null
        val clz = aobj.javaClass
        val flist = clz.declaredFields
        for (f in flist) {
            f.isAccessible = true
            val fobj = f.get(aobj)
            if (fobj != null) {
                if (hitClassWithParent(fobj.javaClass, atype)) {
                    if (additionalCondition(fobj)) {
                        ret = fobj
                    }
                }
            }
        }
        return ret
    }

    /**
     * 查找不确定的类，并对其中的匹配函数进行 hook
     * @param loader Xposed 参数取出的 Classloader
     * @param classNamePattern 类名的Pattern，与 String.format 内使用的格式化字符串形式一致
     * @param start 填充未知部分的起始点（填充时按 +1 递增）
     * @param fieldsig 类的特征成员签名列表
     * @param methodsig 类的特征方法签名列表
     * @param msig 要命中的方法签名
     * @param additionalCondition 附加条件
     * @param hook 勾子函数实体
     * @return 是否 hook 成功
     */
    fun hitUncertenClassAndHookMethod(loader: ClassLoader, classNamePattern: String, start: Char, fieldsig: List<String>? = null, methodsig: List<String>? = null, msig: String, additionalCondition:(Class<*>) -> Boolean, hook: XC_MethodHook): Boolean {
        var ret = false
        var idx = start
        while (true) {
            val hookName = String.format(classNamePattern, idx)
            val clzPanel = try {
                XposedHelpers.findClass(hookName, loader)
            } catch (e: Throwable) {
                null
            } ?: break
            if (additionalCondition(clzPanel)) {
                val info = ClassInfo(clzPanel, fieldsig ?: listOf(), methodsig ?: listOf())
                if (X.hitClass(info)) {
                    if (hitAndHookMethod(info, msig, hook)) {
                        ret = true
                        break
                    }
                }
            }
            idx++
        }
        return ret
    }

    /**
     * 查找不确定的类，并对其中的匹配函数进行 hook
     * @param loader Xposed 参数取出的 Classloader
     * @param classNamePattern 类名的Pattern，与 String.format 内使用的格式化字符串形式一致
     * @param start 填充未知部分的起始点（填充时按 +1 递增）
     * @param fieldsig 类的特征成员签名列表
     * @param methodsig 类的特征方法签名列表
     * @param msig 要命中的方法签名
     * @param additionalCondition 附加条件
     * @param hook 勾子函数实体
     * @return 是否 hook 成功
     */
    fun hitUncertenClassAndHookMethod(loader: ClassLoader, classNamePattern: String, start: Int, fieldsig: List<String>? = null, methodsig: List<String>? = null, msig: String, additionalCondition:(Class<*>) -> Boolean, hook: XC_MethodHook): Boolean {
        var ret = false
        var idx = start
        while (true) {
            val hookName = String.format(classNamePattern, idx)
            val clzPanel = try {
                XposedHelpers.findClass(hookName, loader)
            } catch (e: Throwable) {
                null
            } ?: break
            if (additionalCondition(clzPanel)) {
                val info = ClassInfo(clzPanel, fieldsig ?: listOf(), methodsig ?: listOf())
                if (X.hitClass(info)) {
                    if (hitAndHookMethod(info, msig, hook)) {
                        ret = true
                        break
                    }
                }
            }
            idx++
        }
        return ret
    }

    /**
     * hook 匹配的方法
     * @param aclass 类描述信息
     * @param msig 要命中的方法签名
     * @param hook 勾子函数实体
     * @return 是否 hook 成功
     */
    fun hitAndHookMethod(aclass: ClassInfo, msig: String, hook: XC_MethodHook): Boolean {
        var ret = false
        if (X.hitClass(aclass)) {
            X.hitMethodAndParamClass(aclass.classType, msig) {
                if (it.isNotEmpty() && it.size == 1) {
                    val parr = mutableListOf<Any>()
                    if (it[0].paramClassList.isNotEmpty()) {
                        it[0].paramClassList.forEach { c ->
                            parr.add(c)
                        }
                    }
                    parr.add(hook)
                    val hookArgs = parr.toTypedArray()
                    XposedHelpers.findAndHookMethod(aclass.classType, it[0].methodName, *hookArgs)
                }
                ret = true
            }
        }
        return ret
    }

    fun hookActivityOnCreate(aclass: Class<*>, hook: XC_MethodHook) = XposedHelpers.findAndHookMethod(aclass, "onCreate", Bundle::class.java, hook)

    fun hookActivityOnNewIntent(aclass: Class<*>, hook: XC_MethodHook) = XposedHelpers.findAndHookMethod(aclass, "onNewIntent", Intent::class.java, hook)

    private fun hitClassWithParent(aclz: Class<*>?, atype: Class<*>): Boolean {
        var ret = false
        var tmp: Class<*>? = aclz
        while (true) {
            if (tmp == atype) {
                ret = true
                break
            }
            tmp = tmp?.superclass
            if (tmp == null) {
                break
            }
        }
        return ret
    }
}