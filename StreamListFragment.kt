package com.mycompany.ui.fragment

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.mycompany.R
import com.mycompany.data.model.states.StreamListState
import com.mycompany.databinding.FragmentStreamListBinding
import com.mycompany.ui.activity.MainActivity
import com.mycompany.ui.adapter.StreamListRVAdapter
import com.mycompany.ui.viewmodel.StreamListViewModel
import com.mycompany.utils.MarginItemDecoration
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import javax.inject.Inject

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
@InternalCoroutinesApi
@AndroidEntryPoint
class StreamListFragment : Fragment() {
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    @Inject
    lateinit var streamListAdapter: StreamListRVAdapter

    lateinit var binding: FragmentStreamListBinding
    val viewModel: StreamListViewModel by viewModels()


    private var isAllPermissionGaranted = false

    private val requestMultiplePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->

            if (permissions[Manifest.permission.CAMERA] == true && permissions[Manifest.permission.RECORD_AUDIO] == true) {
                isAllPermissionGaranted = true
            } else {
                Toast.makeText(
                    requireContext(),
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    @ObsoleteCoroutinesApi
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentStreamListBinding.inflate(inflater, container, false)

        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBarHeight = resources.getDimensionPixelSize(resourceId)
        binding.root.setPadding(0, 0, 0, statusBarHeight)
        binding.tvVersion.text = "v. ${getAppVersion()}"
        binding.rvStreams.apply {
            adapter = streamListAdapter
            addItemDecoration(MarginItemDecoration(0, 0, 0, 13))
        }
        binding.toolbar.title = getString(R.string.coming_soon)
        streamListAdapter.setOnItemClickListener {
            if (isAllPermissionGaranted) {
                viewModel.onItemClicked(it)
            } else {
                checkPermission()
            }
        }
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.fetchStreamList()
        }
        binding.toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menu_item_logout -> {
                    viewModel.doLogout()
                }
                else -> {
                }
            }
            true
        }
        viewModelObserve()

        (activity as MainActivity).mNetworkReceiver.isNetworkOnline.observe(viewLifecycleOwner) {
            viewModel.onInternetChangeState(it)
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        checkPermission()
        viewModel.fetchStreamList()
        super.onViewCreated(view, savedInstanceState)
    }

    @ObsoleteCoroutinesApi
    @SuppressLint("NotifyDataSetChanged")
    private fun viewModelObserve() {
        viewModel.streamListStateEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandledOrReturnNull()?.let {
                when (it) {
                    is StreamListState.Error -> {
                        Toast.makeText(requireContext(), it.errorMessage, Toast.LENGTH_LONG).show()
                        binding.progress.hide()
                        binding.swipeRefresh.isRefreshing = false
                    }
                    is StreamListState.Success -> {
                        binding.progress.hide()
                        binding.swipeRefresh.isRefreshing = false
                    }
                    is StreamListState.Logout -> {
                        binding.progress.hide()
                        findNavController().navigate(StreamListFragmentDirections.actionSoonStreamsFragmentToLoginFragment())
                    }
                    is StreamListState.Loading -> {
                        binding.progress.show()
                    }
                }
            }
        }

        viewModel.streamsData.observe(viewLifecycleOwner) {
            streamListAdapter.submitList(it)
            if (binding.swipeRefresh.isRefreshing) {
                binding.swipeRefresh.isRefreshing = false
            }
            streamListAdapter.notifyDataSetChanged()
        }

        viewModel.selectedStreamEvent.observe(viewLifecycleOwner) {
            it.getContentIfNotHandledOrReturnNull()?.let {
                val action =
                    StreamListFragmentDirections.actionSoonStreamsFragmentToPrepareStreamFragment(
                        it.streamId,
                        it.title
                    )
                Log.d("streamId ", "streamList" + it.streamId)
                findNavController().navigate(action)
            }
        }

    }

    private fun checkPermission() {
        if (allPermissionsGranted()) {
            isAllPermissionGaranted = true
        } else {
            requestMultiplePermissions.launch(REQUIRED_PERMISSIONS)
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            requireContext(), it
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun getAppVersion(): String {
        var version = "0"
        try {
            val pInfo = requireContext().packageManager.getPackageInfo(
                requireContext().packageName, 0
            )
            version = pInfo.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return version
    }
}