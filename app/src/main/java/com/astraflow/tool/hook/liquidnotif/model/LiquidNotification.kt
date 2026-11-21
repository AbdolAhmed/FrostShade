package com.astraflow.tool.hook.liquidnotif.model

import android.app.PendingIntent
import android.graphics.Bitmap
import android.widget.RemoteViews

/**
 * 通知卡片底部的一个按钮(通知自带的操作)。
 */
data class LiquidAction(
    /** 按钮上的字;应用只给了图标时为空 */
    val title: String,
    /** 没有字时画的图标(例如音乐的上一首、暂停) */
    val icon: Bitmap?,
    /** 点按时发出的动作 */
    val intent: PendingIntent?,
    /** 这个按钮要先输入文字(直接回复);输入框里的提示字 */
    val replyHint: String?,
    /** 在这条通知全部按钮里的位置,交回系统记录用 */
    val index: Int,
)

/**
 * 通知中心里的一条通知。
 *
 * 只装卡片要画的内容和点按时要交回系统的东西,不带系统界面里的任何类。
 */
data class LiquidNotification(
    /** 系统给这条通知的唯一标识,点开、划掉时交回系统 */
    val key: String,
    /** 发通知的应用 */
    val packageName: String,
    /** 发通知的用户(分身、工作资料各算一个) */
    val userId: Int,
    /** 通知所在的类别(应用的通知渠道),打开通知设置时直接到这一类 */
    val channelId: String?,
    /** 应用名 */
    val appLabel: String,
    /** 应用图标 */
    val appIcon: Bitmap?,
    /** 标题;消息类是发消息的人或会话名 */
    val title: String,
    /** 正文 */
    val text: String,
    /** 发出时间,毫秒 */
    val postTime: Long,
    /** 通知要求显示时间 */
    val showTime: Boolean = true,
    /** 右边的小图(通知自带的大图标,例如头像、封面) */
    val thumbnail: Bitmap? = null,
    /** 进度,0 到 1;没有进度为空 */
    val progress: Float? = null,
    /** 进度不确定(只知道在进行中) */
    val progressIndeterminate: Boolean = false,
    /** 卡片底部的按钮 */
    val actions: List<LiquidAction> = emptyList(),
    /** 能不能划掉;常驻通知不能 */
    val clearable: Boolean = true,
    /** 点开时执行的动作 */
    val contentIntent: PendingIntent? = null,
    /** 这条通知的主色(通知自己给的颜色,没有时取应用图标的颜色);氛围光用 */
    val accent: Int? = null,
    /** 应用自己设计的样式;有它时卡片里放应用自己的排版 */
    val custom: RemoteViews? = null,
    /** 通知内容的版本(每次更新都变),应用自己的排版据此重新套用 */
    val version: Long = postTime,
    /** 通知重要性(0~5,取不到按 0);「按重要性排序」用 */
    val importance: Int = 0,
    /** 系统卡片的高度(像素,卡片还没造出来时为 0);「按卡片高度排序」用 */
    val cardHeightPx: Int = 0,
    /** 列表分区号(同一分区里才重排,分区本身不动);取不到按 0 */
    val section: Int = 0,
)

/**
 * 一组通知:系统合成一组的(这台手机上就是同一应用的多条)堆叠显示,最新的在最前面。
 * 单独一条通知也算一组,只有它自己。
 */
data class LiquidGroup(
    /** 这一组的标识 */
    val id: String,
    val appLabel: String,
    val items: List<LiquidNotification>,
)
