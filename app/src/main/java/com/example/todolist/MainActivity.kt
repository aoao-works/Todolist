package com.example.todolist // ※ご自身のプロジェクトのパッケージ名に書き換えてください

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

// 1. データモデル：ToDoアイテムの設計図
data class TodoItem(val id: Int, val title: String, var isDone: Boolean = false)

// 2. ViewModel：データと処理を管理する場所
class TodoViewModel : ViewModel() {
    private var _todoList = mutableStateListOf<TodoItem>()
    val todoList: List<TodoItem> get() = _todoList
    private var nextId = 1

    // タスクを追加
    fun addTodo(title: String) {
        if (title.isNotBlank()) {
            _todoList.add(TodoItem(id = nextId++, title = title))
        }
    }

    // タスクの完了/未完了を切り替え
    fun toggleTodo(id: Int) {
        val index = _todoList.indexOfFirst { it.id == id }
        if (index != -1) {
            _todoList[index] = _todoList[index].copy(isDone = !_todoList[index].isDone)
        }
    }

    // タスクを削除
    fun removeTodo(id: Int) {
        _todoList.removeAll { it.id == id }
    }
}

// 3. Activity：アプリの入り口
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                // 画面全体の背景色などを設定
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TodoScreen()
                }
            }
        }
    }
}

// 4. UI（画面）：見た目と操作の組み立て
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(viewModel: TodoViewModel = viewModel()) {
    // 入力欄のテキスト状態を保持
    var inputText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("シンプルToDo") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // 入力エリア
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("新しいタスクを入力") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    viewModel.addTodo(inputText)
                    inputText = "" // 追加したら入力欄を空にする
                }) {
                    Text("追加")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // リスト表示エリア
            LazyColumn {
                items(viewModel.todoList, key = { it.id }) { todo ->
                    TodoRow(
                        todo = todo,
                        onToggle = { viewModel.toggleTodo(todo.id) },
                        onDelete = { viewModel.removeTodo(todo.id) }
                    )
                }
            }
        }
    }
}

// 各タスクの行の見た目
@Composable
fun TodoRow(todo: TodoItem, onToggle: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = todo.isDone,
            onCheckedChange = { onToggle() }
        )
        Text(
            text = todo.title,
            modifier = Modifier.weight(1f),
            // 完了したタスクには取り消し線を引く
            textDecoration = if (todo.isDone) TextDecoration.LineThrough else TextDecoration.None
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "削除")
        }
    }
}