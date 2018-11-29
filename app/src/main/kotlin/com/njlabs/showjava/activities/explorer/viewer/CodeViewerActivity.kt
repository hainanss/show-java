/*
 * Show Java - A java/apk decompiler for android
 * Copyright (c) 2018 Niranjan Rajendran
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.njlabs.showjava.activities.explorer.viewer

import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.Menu
import android.view.MenuItem
import android.view.View
import com.njlabs.showjava.R
import com.njlabs.showjava.activities.BaseActivity
import com.njlabs.showjava.utils.rx.ProcessStatus
import com.njlabs.showjava.utils.views.CodeView
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_code_viewer.*
import timber.log.Timber
import java.io.File

class CodeViewerActivity : BaseActivity(), CodeView.OnHighlightListener {

    private var fileLoaderSubscription: Disposable? = null
    private var extensionTypeMap = hashMapOf(
        "txt" to "plaintext",
        "class" to "java",
        "yml" to "yaml",
        "md" to "markdown"
    )
    private var darkMode = true

    override fun init(savedInstanceState: Bundle?) {

        setupLayout(R.layout.activity_code_viewer)
        val extras = intent.extras

        val file = File(extras?.getString("filePath"))
        val packageName = extras?.getString("name")

        toolbar.popupTheme = R.style.AppTheme_DarkPopupOverlay

        supportActionBar?.title = file.name
        val subtitle = file.canonicalPath.replace(
             "${Environment.getExternalStorageDirectory()}/show-java/sources/$packageName/",
            ""
        )

        // Workaround for a weird bug caused by Calligraphy
        // https://github.com/chrisjenx/Calligraphy/issues/280#issuecomment-256444828
        toolbar.post {
            toolbar.subtitle = subtitle
            if (file.name.trim().equals("AndroidManifest.xml", true)) {
                toolbar.subtitle = packageName
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            codeView.visibility = View.INVISIBLE
            codeLoadProgress.visibility = View.VISIBLE
        } else {
            codeView.visibility = View.VISIBLE
            codeLoadProgress.visibility = View.GONE
        }

        var language = file.extension
        extensionTypeMap[language]?.let {
            language = it
        }

        fileLoaderSubscription = loadFile(file)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnError { error -> Timber.e(error) }
            .subscribe { status ->
                codeView.setCode(status.result)
                    .setLanguage(language)
                    .setWrapLine(false)
                    .setFontSize(14F)
                    .setZoomEnabled(true)
                    .setShowLineNumber(true)
                    .setOnHighlightListener(this)
                    .load()
            }
    }

    private fun loadFile(fileToLoad: File): Observable<ProcessStatus<String>> {
        return Observable.create { emitter: ObservableEmitter<ProcessStatus<String>> ->
            emitter.onNext(ProcessStatus(fileToLoad.readText()))
            emitter.onComplete()
        }
    }

    override fun onFontSizeChanged(sizeInPx: Int) {}
    override fun onLineClicked(lineNumber: Int, content: String) {}

    override fun onStartCodeHighlight() {
        runOnUiThread {
            codeView.visibility = View.INVISIBLE
            codeLoadProgress.visibility = View.VISIBLE
        }
    }

    override fun onFinishCodeHighlight() {
        runOnUiThread {
            codeView.visibility = View.VISIBLE
            codeLoadProgress.visibility = View.GONE
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        menu.findItem(R.id.wrap_text).isVisible = true
        menu.findItem(R.id.invert_colors).isVisible = true
        menu.findItem(R.id.zoomable).isVisible = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            menu.findItem(R.id.line_number).isVisible = true
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.wrap_text -> {
                val newState = !item.isChecked
                codeView.setWrapLine(newState).apply()
                item.isChecked = newState
                return true
            }
            R.id.zoomable -> {
                val newState = !item.isChecked
                codeView.setZoomEnabled(newState)
                item.isChecked = newState
                return true
            }
            R.id.line_number -> {
                val newState = !item.isChecked
                codeView.setShowLineNumber(newState).apply()
                item.isChecked = newState
                return true
            }
            R.id.invert_colors -> {
                darkMode = !darkMode
                codeView.setDarkMode(darkMode).apply()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (fileLoaderSubscription?.isDisposed != true) {
            fileLoaderSubscription?.dispose()
        }
    }
}