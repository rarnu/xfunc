@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.rarnu.xfunc

import android.os.Build
import android.util.Log
import com.rarnu.kt.android.runCommand
import java.lang.reflect.Field
import java.lang.reflect.Method

object X {

    private const val TAG = "xfunc"

    /**
     * 在 xposed 内启动 Activity
     * @param componentName 要启动的组件名，格式必须为 包名/组件完整类名
     */
    fun startActivity(componentName: String) {
        runCommand {
            runAsRoot = true
            commands.add("am start -n $componentName")
            result { _, error ->
                if (error != "") {
                    Log.e(TAG, "am activity error => $error")
                }
            }
        }
    }

    /**
     * 在 xposed 内启动 Service
     * @param componentName 要启动的组件名，格式必须为 包名/组件完整类名
     */
    fun startService(componentName: String) {
        runCommand {
            runAsRoot = true
            commands.add("am ${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) "start-foreground-service" else "startservice"} -n $componentName")
            result { _, error ->
                if (error != "") {
                    Log.e(TAG, "am service error => $error")
                }
            }
        }
    }

    /**
     * 在 xposed 内发送广播
     * @param action 广播的action
     * @param params 广播的Intent参数
     */
    fun sendBroadcast(action: String, params: Map<String, String>?) {
        var pstr = ""
        params?.forEach { k, v -> pstr += "-e $k \"$v\" " }
        runCommand {
            runAsRoot = true
            commands.add("am broadcast -a $action $pstr")
            result { _, error ->
                if (error != "") {
                    Log.e(TAG, "am broadcast error => $error")
                }
            }
        }
    }

    /**
     * 在类内搜索指定类型的Field
     */
    fun findComponentInClass(aclass: Class<*>, acomponentclass: Class<*>): List<Field> {
        val flist = aclass.declaredFields
        val ret = mutableListOf<Field>()
        flist.forEach {
            if (it.type == acomponentclass) {
                ret.add(it)
            }
        }
        return ret
    }

    /**
     * 在对象内搜索指定类型的内部对象
     */
    fun findComponentInObject(aobj: Any, acomponentclass: Class<*>): List<Any> {
        val flist = aobj.javaClass.declaredFields
        val ret = mutableListOf<Any>()
        flist.forEach {
            if (it.type == acomponentclass) {
                ret.add(it.get(aobj))
            }
        }
        return ret
    }

    /**
     * 根据方法信息判断类型是否命中
     * @param msiglist 函数签名
     */
    fun hitClassByMethods(aclass: Class<*>, msiglist: List<String>): Boolean {
        val mlist = aclass.declaredMethods
        if (mlist.size < msiglist.size) {
            return false
        }
        val tmplist = msiglist.toMutableList()
        for (i in mlist.indices) {
            val idx = isMethodMatchList(mlist[i], tmplist)
            if (idx != -1) {
                tmplist.removeAt(idx)
            }
        }
        if (tmplist.isEmpty()) {
            // all deleted (all matched)
            return true
        }
        return false
    }

    /**
     * 根据方法信息判断类型是否命中
     * @param aclass 类型描述信息
     */
    fun hitClassByMethods(aclass: ClassInfo) = hitClassByMethods(aclass.classType, aclass.methodSig)

    /**
     * 根据成员变量信息判断类型是否命中
     * @param fsiglist 成员变量签名
     */
    fun hitClassByFields(aclass: Class<*>, fsiglist: List<String>): Boolean {
        val flist = aclass.declaredFields
        if (flist.size < fsiglist.size) {
            return false
        }
        val tmplist = fsiglist.toMutableList()
        for (i in flist.indices) {
            val idx = isFieldMatchList(flist[i], tmplist)
            if (idx != -1) {
                tmplist.removeAt(idx)
            }
        }
        if (tmplist.isEmpty()) {
            // all deleted (all matched)
            return true
        }
        return false
    }

    /**
     * 根据成员变量信息判断类型是否命中
     * @param aclass 类型描述信息
     */
    fun hitClassByFields(aclass: ClassInfo) = hitClassByFields(aclass.classType, aclass.fieldSig)

    /**
     * 极据方法和成员变量信息来判断类型是否命中
     */
    fun hitClass(aclass: Class<*>, msiglist: List<String>, fsiglist: List<String>): Boolean {
        val hitm = if (msiglist.isNotEmpty()) hitClassByMethods(aclass, msiglist) else true
        val hitf = if (fsiglist.isNotEmpty()) hitClassByFields(aclass, fsiglist) else true
        return (hitm && hitf)
    }

    /**
     * 极据方法和成员变量信息来判断类型是否命中
     */
    fun hitClass(aclass: ClassInfo) = hitClass(aclass.classType, aclass.methodSig, aclass.fieldSig)

    /**
     * 判断方法是否命中签名，命中的情况下，返回方法的参数信息
     */
    fun hitMethodAndParamClass(aclass: Class<*>, asig: String, callback: (List<MethodInfo>) -> Unit): Boolean {
        val mlist = aclass.declaredMethods
        val retlist = arrayListOf<MethodInfo>()
        var ret = false
        mlist.forEach {
            if (isMethodMatchSig(it, asig)) {
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

    private fun isMethodMatchSig(amethod: Method, asig: String): Boolean {
        val plist = amethod.parameterTypes
        val pret = amethod.returnType.name
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

    private fun isMethodMatchList(amethod: Method, asiglist: List<String>): Int {
        var idx = -1
        for (i in asiglist.indices) {
            if (isMethodMatchSig(amethod, asiglist[i])) {
                idx = i
                break
            }
        }
        return idx
    }

    private fun isFieldMatchList(afield: Field, asiglist: List<String>): Int {
        var idx = -1
        for (i in asiglist.indices) {
            if (afield.type.name.contains(asiglist[i])) {
                idx = i
                break
            }
        }
        return idx
    }

}