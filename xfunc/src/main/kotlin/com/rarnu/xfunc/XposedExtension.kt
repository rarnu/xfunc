@file:Suppress("HasPlatformType", "unused", "Duplicates")

package com.rarnu.xfunc

import android.content.res.XResources
import android.os.Build
import com.rarnu.kt.android.runCommand
import de.robv.android.xposed.*
import de.robv.android.xposed.callbacks.XC_InitPackageResources
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.Method

//=========================================
// typealias
//=========================================
typealias XposedPkg = XC_LoadPackage.LoadPackageParam
typealias XposedRes = XC_InitPackageResources.InitPackageResourcesParam
typealias XposedStartup = IXposedHookZygoteInit.StartupParam

//=========================================
// data classes
//=========================================
data class ClassInfo(val classType: Class<*>, val fieldSig: List<String>, val methodSig: List<String>)
data class MethodInfo(var methodName: String = "", val paramClassList: MutableList<Class<*>> = mutableListOf(), var returnClass: Class<*>? = null)
data class MethodExt(val c: Class<*>, val m: Executable, val isConstructor: Boolean)
data class FieldExt(val c: Class<*>, val f: Field)
data class MethodNameExt(val c: Class<*>, val m: String, val isConstructor: Boolean)
data class Hook(val thisClass: Class<*>, val thisMethod: Executable, val isConstructor: Boolean)
data class HookAll(val thisClass: Class<*>, val thisMethodName: String, val isConstructor: Boolean)

//=========================================
// Class extensions
//=========================================
fun Class<*>.findMethod(name: String, vararg param: Class<*>) = MethodExt(this, getDeclaredMethod(name, *param), false)
fun Class<*>.findAllMethod(name: String) = MethodNameExt(this, name, false)
fun Class<*>.findConstructor(vararg param: Class<*>) = MethodExt(this, getDeclaredConstructor(*param), true)
fun Class<*>.findAllConstructor() = MethodNameExt(this, "", true)
fun Class<*>.findField(name: String) = FieldExt(this, getDeclaredField(name))
fun Class<*>.findComponent(acomponentclass: Class<*>) = declaredFields.filter { it.type == acomponentclass }
/**
 * 根据方法信息判断类型是否命中
 * @param msiglist 函数签名
 */
fun Class<*>.hitByMethods(msiglist: List<String>): Boolean {
    val mlist = declaredMethods
    if (mlist.size < msiglist.size) {
        return false
    }
    val tmplist = msiglist.toMutableList()
    for (i in mlist.indices) {
        val idx = mlist[i].isMatchList(tmplist)
        if (idx != -1) {
            tmplist.removeAt(idx)
        }
    }
    if (tmplist.isEmpty()) {
        return true
    }
    return false
}

/**
 * 根据成员变量信息判断类型是否命中
 * @param fsiglist 成员变量签名
 */
fun Class<*>.hitByFields(fsiglist: List<String>): Boolean {
    val flist = declaredFields
    if (flist.size < fsiglist.size) {
        return false
    }
    val tmplist = fsiglist.toMutableList()
    for (i in flist.indices) {
        val idx = flist[i].isMatchList(tmplist)
        if (idx != -1) {
            tmplist.removeAt(idx)
        }
    }
    if (tmplist.isEmpty()) {
        return true
    }
    return false
}

/**
 * 极据方法和成员变量信息来判断类型是否命中
 */
fun Class<*>.hit(msiglist: List<String>, fsiglist: List<String>): Boolean {
    val hitm = if (msiglist.isNotEmpty()) hitByMethods(msiglist) else true
    val hitf = if (fsiglist.isNotEmpty()) hitByFields(fsiglist) else true
    return (hitm && hitf)
}

/**
 * 判断方法是否命中签名，命中的情况下，返回方法的参数信息
 */
fun Class<*>.hitMethodParam(asig: String, callback: (List<MethodInfo>) -> Unit): Boolean {
    val mlist = declaredMethods
    val retlist = arrayListOf<MethodInfo>()
    var ret = false
    mlist.forEach {
        if (it.isMatchSig(asig)) {
            ret = true
            val info = MethodInfo()
            info.methodName = it.name
            info.returnClass = it.returnType
            val plist = it.parameterTypes
            if (plist.isNotEmpty()) {
                info.paramClassList.addAll(plist)
            }
            retlist.add(info)
        }
    }
    if (ret) {
        callback(retlist)
    }
    return ret
}

private fun Class<*>.hitWithParent(atype: Class<*>): Boolean {
    var ret = false
    var tmp: Class<*>? = this
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

//=========================================
// ClassInfo extensions
//=========================================
/**
 * 极据方法和成员变量信息来判断类型是否命中
 */
fun ClassInfo.hit() = classType.hit(methodSig, fieldSig)

/**
 * 根据方法信息判断类型是否命中
 * @param aclass 类型描述信息
 */
fun ClassInfo.hitByMethods(aclass: ClassInfo) = aclass.classType.hitByMethods(aclass.methodSig)



/**
 * 根据成员变量信息判断类型是否命中
 */
fun ClassInfo.hitByFields() = classType.hitByFields(fieldSig)

/**
 * hook 匹配的方法
 * @param msig 要命中的方法签名
 * @param hook 勾子函数实体
 * @return 是否 hook 成功
 */
fun ClassInfo.hitHookMethod(msig: String, hook: XC_MethodHook): Boolean {
    var ret = false
    if (hit()) {
        classType.hitMethodParam(msig) {
            if (it.isNotEmpty() && it.size == 1) {
                val parr = mutableListOf<Any>()
                if (it[0].paramClassList.isNotEmpty()) {
                    it[0].paramClassList.forEach { c ->
                        parr.add(c)
                    }
                }
                parr.add(hook)
                val hookArgs = parr.toTypedArray()
                XposedHelpers.findAndHookMethod(classType, it[0].methodName, *hookArgs)
            }
            ret = true
        }
    }
    return ret
}

//=========================================
// Field extensions
//=========================================
private fun Field.isMatchList(asiglist: List<String>): Int {
    var idx = -1
    for (i in asiglist.indices) {
        if (type.name.contains(asiglist[i])) {
            idx = i
            break
        }
    }
    return idx
}

//=========================================
// Method extensions
//=========================================
private fun Method.isMatchList(asiglist: List<String>): Int {
    var idx = -1
    for (i in asiglist.indices) {
        if (isMatchSig(asiglist[i])) {
            idx = i
            break
        }
    }
    return idx
}

private fun Method.isMatchSig(asig: String): Boolean {
    val plist = parameterTypes
    val pret = returnType.name
    val sigret = asig.substring(asig.indexOf(')') + 1)
    val sigstr = asig.substring(1, asig.indexOf(')'))
    val siglist = if (sigstr == "") listOf() else sigstr.split(',')
    if (plist.size != siglist.size) {
        return false
    }
    if (!pret.contains(sigret)) {
        return false
    }
    for (i in plist.indices) {
        if (!plist[i].name.contains(siglist[i])) {
            return false
        }
    }
    return true
}

//=========================================
// Any extensions
//=========================================
fun Any.findComponent(acomponentclass: Class<*>) = javaClass.declaredFields.filter { it.type == acomponentclass }.map { it.get(this) }

/**
 * 从类实例中查找指定类型的实例
 * @param atype 要查找的类型
 * @param additionalCondition 附加条件
 * @return 命中的类内实例
 */
fun Any.hit(atype: Class<*>, additionalCondition: (Any) -> Boolean): Any? {
    var ret: Any? = null
    val clz = javaClass
    val flist = clz.declaredFields
    for (f in flist) {
        f.isAccessible = true
        val fobj = f.get(this)
        if (fobj != null) {
            if (fobj.javaClass.hitWithParent(atype)) {
                if (additionalCondition(fobj)) {
                    ret = fobj
                }
            }
        }
    }
    return ret
}

//=========================================
// ClassLoader extensions
//=========================================

/**
 * 查找不确定的类，并对其中的匹配函数进行 hook
 * @param classNamePattern 类名的Pattern，与 String.format 内使用的格式化字符串形式一致
 * @param start 填充未知部分的起始点（填充时按 +1 递增）
 * @param fieldsig 类的特征成员签名列表
 * @param methodsig 类的特征方法签名列表
 * @param msig 要命中的方法签名
 * @param additionalCondition 附加条件
 * @param hook 勾子函数实体
 * @return 是否 hook 成功
 */
fun ClassLoader.hitUncertenClassHookMethod(classNamePattern: String, start: Char, fieldsig: List<String>? = null, methodsig: List<String>? = null, msig: String, additionalCondition:(Class<*>) -> Boolean, hook: XC_MethodHook): Boolean {
    var ret = false
    var idx = start
    while (true) {
        val hookName = String.format(classNamePattern, idx)
        val clzPanel = try {
            XposedHelpers.findClass(hookName, this)
        } catch (e: Throwable) {
            null
        } ?: break
        if (additionalCondition(clzPanel)) {
            val info = ClassInfo(clzPanel, fieldsig ?: listOf(), methodsig ?: listOf())
            if (info.hit()) {
                if (info.hitHookMethod(msig, hook)) {
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
 * @param classNamePattern 类名的Pattern，与 String.format 内使用的格式化字符串形式一致
 * @param start 填充未知部分的起始点（填充时按 +1 递增）
 * @param fieldsig 类的特征成员签名列表
 * @param methodsig 类的特征方法签名列表
 * @param msig 要命中的方法签名
 * @param additionalCondition 附加条件
 * @param hook 勾子函数实体
 * @return 是否 hook 成功
 */
fun ClassLoader.hitUncertenClassAndHookMethod(classNamePattern: String, start: Int, fieldsig: List<String>? = null, methodsig: List<String>? = null, msig: String, additionalCondition:(Class<*>) -> Boolean, hook: XC_MethodHook): Boolean {
    var ret = false
    var idx = start
    while (true) {
        val hookName = String.format(classNamePattern, idx)
        val clzPanel = try {
            XposedHelpers.findClass(hookName, this)
        } catch (e: Throwable) {
            null
        } ?: break
        if (additionalCondition(clzPanel)) {
            val info = ClassInfo(clzPanel, fieldsig ?: listOf(), methodsig ?: listOf())
            if (info.hit()) {
                if (info.hitHookMethod(msig, hook)) {
                    ret = true
                    break
                }
            }
        }
        idx++
    }
    return ret
}

//=========================================
// Extension of Xposed Extensions
//=========================================
fun MethodExt.hook(operator: Hook.() -> Unit) = operator(Hook(c, m, isConstructor))
fun MethodNameExt.hook(operator: HookAll.() -> Unit) = operator(HookAll(c, m, isConstructor))
fun MethodExt.invoke(aobj: Any, vararg params: Any?) = XposedHelpers.callMethod(aobj, m.name, m.parameterTypes, *params)
fun MethodExt.invokeStatic(vararg params: Any?) = XposedHelpers.callStaticMethod(c, m.name, m.parameterTypes, *params)
fun FieldExt.get(aobj: Any) = XposedHelpers.getObjectField(aobj, f.name)
fun FieldExt.set(aobj: Any, avalue: Any?) = XposedHelpers.setObjectField(aobj, f.name, avalue)
fun FieldExt.getStatic() = XposedHelpers.getStaticObjectField(c, f.name)
fun FieldExt.setStatic(avalue: Any?) = XposedHelpers.setStaticObjectField(c, f.name, avalue)


private fun Hook.execute(list: List<Any>) = if (isConstructor) {
    XposedHelpers.findAndHookConstructor(thisClass, *list.toTypedArray())
} else {
    XposedHelpers.findAndHookMethod(thisClass, thisMethod.name, *list.toTypedArray())
}

fun Hook.before(beforeOperation: XC_MethodHook.MethodHookParam.() -> Unit) {
    val hkBefore = object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            beforeOperation(param)
        }
    }
    val plist = mutableListOf<Any>()
    plist.addAll(thisMethod.parameterTypes)
    plist.add(hkBefore)
    execute(plist)
}

fun Hook.after(afterOperation: XC_MethodHook.MethodHookParam.() -> Unit) {
    val hkAfter = object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            afterOperation(param)
        }
    }
    val plist = mutableListOf<Any>()
    plist.addAll(thisMethod.parameterTypes)
    plist.add(hkAfter)
    execute(plist)
}

fun Hook.replace(replaceOperation: XC_MethodHook.MethodHookParam.() -> Unit) {
    val hkReplace = object : XC_MethodReplacement() {
        override fun replaceHookedMethod(param: MethodHookParam): Any? {
            replaceOperation(param)
            return param.result
        }
    }
    val plist = mutableListOf<Any>()
    plist.addAll(thisMethod.parameterTypes)
    plist.add(hkReplace)
    execute(plist)
}

private fun HookAll.execute(mh: XC_MethodHook) = if (isConstructor) {
    XposedBridge.hookAllConstructors(thisClass, mh)
} else {
    XposedBridge.hookAllMethods(thisClass, thisMethodName, mh)
}

fun HookAll.before(beforeOperation: XC_MethodHook.MethodHookParam.() -> Unit) {
    val hkBefore = object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            beforeOperation(param)
        }
    }
    execute(hkBefore)
}

fun HookAll.after(afterOperation: XC_MethodHook.MethodHookParam.() -> Unit) {
    val hkAfter = object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            afterOperation(param)
        }
    }
    execute(hkAfter)
}

fun HookAll.replace(replaceOperation: XC_MethodHook.MethodHookParam.() -> Unit) {
    val hkReplace = object : XC_MethodReplacement() {
        override fun replaceHookedMethod(param: MethodHookParam): Any? {
            replaceOperation(param)
            return param.result
        }
    }
    execute(hkReplace)
}

//=========================================
// Xposed Internal Extensions
//=========================================

fun XC_LoadPackage.LoadPackageParam.findClass(name: String) = XposedHelpers.findClass(name, classLoader)
fun IXposedHookZygoteInit.StartupParam.findClass(name: String) = XposedHelpers.findClass(name, null)
fun XC_InitPackageResources.InitPackageResourcesParam.edit(operator: XResources.() -> Unit) = operator(res)

fun XC_LoadPackage.LoadPackageParam.startActivity(componentName: String) = runCommand {
    runAsRoot = true
    commands.add("am start -n $componentName")
}

fun XC_LoadPackage.LoadPackageParam.startService(componentName: String) = runCommand {
    runAsRoot = true
    commands.add("am ${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) "start-foreground-service" else "startservice"} -n $componentName")
}

fun XC_LoadPackage.LoadPackageParam.sendBroadcast(action: String, params: Map<String, String>?) {
    var pstr = ""
    params?.forEach { k, v -> pstr += "-e $k \"$v\" " }
    runCommand {
        runAsRoot = true
        commands.add("am broadcast -a $action $pstr")
    }
}

//=========================================
// Abstract Xposed Classed
//=========================================

abstract class XposedPackage : IXposedHookLoadPackage {
    abstract fun hook(pkg: XposedPkg)
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) = hook(lpparam)
}

abstract class XposedResource : IXposedHookInitPackageResources {
    abstract fun hook(res: XposedRes)
    override fun handleInitPackageResources(resparam: XC_InitPackageResources.InitPackageResourcesParam) = hook(resparam)
}

abstract class XposedZygote : IXposedHookZygoteInit {
    abstract fun hook(zygote: XposedStartup)
    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) = hook(startupParam)
}
