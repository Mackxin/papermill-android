# PaperMill 混淆规则（v0.17.1 起 release 打开 R8）
#
# 这个 App 全是 Kotlin + Compose + org.json，没有反射、没有 Gson/Retrofit 的
# 注解解析、没有 JS 桥，所以规则可以非常薄 —— 只需要保证启动类不被裁掉。
# Compose / AndroidX 的 keep 规则由各自的 AAR 自带，不用手写。

# Activity 由 AndroidManifest 引用，R8 自己会保；这里显式写上，
# 免得将来有人改成动态注册时踩空
-keep class com.papermill.app.MainActivity { *; }

# 数据类是纯 JSON 手写序列化（LocalStore），字段名不参与反射，无需 keep。
# 但如果哪天换成 @Serializable/反射解析，这里要补上 —— 留个记号。
