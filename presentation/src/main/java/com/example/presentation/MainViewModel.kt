package com.example.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.usecase.GetFollowerListUseCase
import com.example.domain.usecase.GetUserInfoUseCase
import com.example.domain.usecase.SearchUserUseCase
import com.example.presentation.model.FollowerUiModel
import com.example.presentation.model.SearchUiModel
import com.example.presentation.model.UserUiModel
import com.example.presentation.model.toPresentation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * CoroutineStudy
 * @author jaesung
 * @created 2023/04/28
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val getFollowerListUseCase: GetFollowerListUseCase,
    private val getUserInfoUseCase: GetUserInfoUseCase,
    private val searchUserUseCase: SearchUserUseCase,
) : ViewModel() {

    private val _uiStateFlow: MutableStateFlow<UiState> = MutableStateFlow(UiState.Loading)
    val uiStateFlow: StateFlow<UiState> = _uiStateFlow.asStateFlow()

    private fun getUserInfo(userName: String): Flow<UserUiModel> =
        getUserInfoUseCase(userName).map { it.toPresentation() }

    private fun getFollowers(userName: String): Flow<List<FollowerUiModel>> =
        getFollowerListUseCase(userName).map { followerList ->
            followerList.map { follower ->
                follower.toPresentation()
            }
        }

    private fun searchUsers(userName: String): Flow<SearchUiModel> =
        searchUserUseCase(userName).map {
            it.toPresentation()
        }

    @OptIn(FlowPreview::class)
    fun searchUser(userName: String) {
        viewModelScope.launch {
            searchUsers(userName).flatMapMerge { searchResult ->
                val userNameList = searchResult.items.map { it.login }
                combineUserInfoWithFollowers(userNameList)
            }.catch {
                _uiStateFlow.value = UiState.Error(it.message ?: "Error")
            }.collect {
                _uiStateFlow.value = UiState.Success(it)
            }
        }
    }

    private fun combineUserInfoWithFollowers(userNameList: List<String>) = flow {
        val size = userNameList.size
        val uiStateList = mutableListOf<SearchUiState>()
        userNameList.forEach { name ->
            combine(getUserInfo(name), getFollowers(name)) { user, followers ->
                SearchUiState(
                    userName = user.login,
                    avatarUrl = user.avatarUrl,
                    followerCount = user.followers,
                    followers = followers.map { follower ->
                        SearchUiState.Follower(
                            userName = follower.login,
                            avatarUrl = follower.avatarUrl,
                            githubUrl = follower.htmlUrl,
                        )
                    }
                )
            }.collect { searchUiState ->
                uiStateList.add(searchUiState)
                if (uiStateList.size == size) {
                    emit(uiStateList.toList())
                }
            }
        }
    }
}

sealed class UiState {
    object Loading : UiState()
    data class Success(val uiState: List<SearchUiState>) : UiState()
    data class Error(val errorMessage: String) : UiState()
}