package com.mycompany.ui.fragment

import android.hardware.Camera
import android.os.Build
import android.os.Bundle
import android.util.Size
import android.view.LayoutInflater
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.mycompany.data.managers.SessionManager
import com.mycompany.databinding.FragmentPrepareStreamBinding
import com.mycompany.ui.activity.MainActivity
import com.mycompany.ui.viewmodel.VideoStreamViewModel
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.encoder.input.video.CameraOpenException
import com.pedro.rtplibrary.rtmp.RtmpCamera1
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.InternalCoroutinesApi
import net.ossrs.rtmp.ConnectCheckerRtmp
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt


@InternalCoroutinesApi
@AndroidEntryPoint
class PrepareStreamFragment : Fragment(), SurfaceHolder.Callback, ConnectCheckerRtmp {


    lateinit var binding: FragmentPrepareStreamBinding
    val viewModel: VideoStreamViewModel by activityViewModels()
    val args: PrepareStreamFragmentArgs by navArgs()

    @Inject
    lateinit var sessionManager: SessionManager

    private lateinit var rtmpCameraPreview: RtmpCamera1
    private var isFrontCamera = true


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPrepareStreamBinding.inflate(inflater, container, false)
        (activity as MainActivity).mNetworkReceiver.isNetworkOnline.observe(viewLifecycleOwner) {
            viewModel.onInternetIsOn(it)
        }
        binding.btnStart.setOnClickListener {
            viewModel.fetchStreamData(args.streamId)
        }
        binding.btnClose.setOnClickListener {
            findNavController().popBackStack()
        }
        binding.btnCancel.setOnClickListener {
            findNavController().popBackStack()
        }
        viewModelObserve()
        binding.tvTitle.text = args.streamTitle
        makeCameraPreview()
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        binding.progress.hide()
    }

    override fun onStop() {
        super.onStop()
        if (::rtmpCameraPreview.isInitialized) {
            rtmpCameraPreview.stopPreview()
        }

    }

    private fun viewModelObserve() {
        binding.progress.hide()
        viewModel.prepareStateData.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandledOrReturnNull()?.let {
                when {
                    it.isError -> {
                        binding.progress.hide()
                        val toast =
                            Toast.makeText(requireContext(), it.errorMessage, Toast.LENGTH_LONG)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            toast.addCallback(object : Toast.Callback() {
                                override fun onToastHidden() {
                                    super.onToastHidden()
                                    findNavController().popBackStack()
                                }
                            })
                            toast.show()
                        } else {
                            toast.show()
                            findNavController().popBackStack()
                        }

                        if (::rtmpCameraPreview.isInitialized) {
                            rtmpCameraPreview.stopPreview()
                        }

                    }
                    it.isLoading -> {
                        binding.progress.show()
                    }
                    it.isStopPreview -> {
                        if (::rtmpCameraPreview.isInitialized) {
                            rtmpCameraPreview.stopPreview()
                        }
                        binding.progress.hide()
                    }
                    else -> {
                        if (::rtmpCameraPreview.isInitialized) {
                            rtmpCameraPreview.stopPreview()
                            rtmpCameraPreview.disableAudio()
                        }
                        binding.progress.hide()
                        viewModel.clearVideoStreamState()
                        val action =
                            PrepareStreamFragmentDirections.actionPrepareStreamFragmentToVideoStreamFragment(
                                args.streamTitle,
                                rtmpCameraPreview.isFrontCamera
                            )
                        findNavController().navigate(action)

                    }
                }
            }
        }
    }

    private fun makeCameraPreview() {
        rtmpCameraPreview = RtmpCamera1(binding.cameraPreview, this)

        try {
            val resolution = rtmpCameraPreview.resolutionsFront.sortedBy { it.height }
                .filter { it.height > 1000 }.first()
            if (!rtmpCameraPreview.prepareAudio(
                    160,
                    44100,
                    true
                ) || !rtmpCameraPreview.prepareVideo(
                    resolution.width,
                    resolution.height,
                    30,
                    2500,
                    90
                )
            ) {
                if (::sessionManager.isInitialized) {
                    if (sessionManager.getIsDevMode()) {
                        //Toast.makeText(requireContext(), "Failed set resolution to ${resolution.width}x${resolution.height}\n\n Resolutions: ${rtmpCameraPreview.resolutionsFront.toString()}", Toast.LENGTH_LONG).show()
                    }
                }

                throw CameraOpenException("Error prepare Camera")
            } else {
                if (::sessionManager.isInitialized) {
                    if (sessionManager.getIsDevMode()) {
                        //Toast.makeText(requireContext(), "Set resolution to ${resolution.width}x${resolution.height}\n resolutions: ${rtmpCameraPreview.resolutionsFront.toString()}", Toast.LENGTH_LONG).show()
                    }
                }
                //Toast.makeText(requireContext(), "Set camera resolution ${rtmpCameraPreview.streamWidth}x${rtmpCameraPreview.streamHeight} resolution value is ${rtmpCameraPreview.resolutionValue}", Toast.LENGTH_LONG).show()
            }
        } catch (ex: CameraOpenException) {
            try {
                if (!rtmpCameraPreview.prepareAudio() || !rtmpCameraPreview.prepareVideo()) {
                    throw CameraOpenException("Error prepare Camera")
                } else {
                    //Toast.makeText(requireContext(), "Set camera resolution ${rtmpCameraPreview.streamWidth}x${rtmpCameraPreview.streamHeight} resolution value is ${rtmpCameraPreview.resolutionValue}", Toast.LENGTH_LONG).show()
                }
            } catch (e: java.lang.Exception) {
                viewModel.onErrorPrepareStream()
            }
        } catch (e: java.lang.Exception) {
            viewModel.onErrorPrepareStream()
        }
        rtmpCameraPreview.stopPreview()
        binding.cameraPreview.holder.addCallback(this)

    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        try {
            val resolution =
                rtmpCameraPreview.resolutionsFront.sortedBy { it.height }
                    .filter { it.height > 1000 }
                    .first()
            rtmpCameraPreview.startPreview(
                CameraHelper.Facing.FRONT,
                resolution.width,
                resolution.height
            )
            val newSize = getPreviewSize(resolution)
            binding.cameraPreview.layoutParams =
                ConstraintLayout.LayoutParams(newSize.width, newSize.height)
        } catch (ex: CameraOpenException) {
            try {
                getPreviewSize(null)
                rtmpCameraPreview.startPreview(CameraHelper.Facing.FRONT)
            } catch (e: Exception) {
                viewModel.onErrorPrepareStream()
            }
        } catch (e: java.lang.Exception) {
            viewModel.onErrorPrepareStream()
        }
    }

    private fun getOptimalPreviewSize(sizes: List<Camera.Size>?, w: Int, h: Int): Camera.Size? {
        val ASPECT_TOLERANCE = 0.1
        val targetRatio = h.toDouble() / w
        if (sizes == null) return null
        var optimalSize: Camera.Size? = null
        var minDiff = Double.MAX_VALUE
        for (size in sizes) {
            val ratio = size.width.toDouble() / size.height
            if (abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue
            if (abs(size.height - h) < minDiff) {
                optimalSize = size
                minDiff = abs(size.height - h).toDouble()
            }
        }
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE
            for (size in sizes) {
                if (abs(size.height - h) < minDiff) {
                    optimalSize = size
                    minDiff = abs(size.height - h).toDouble()
                }
            }
        }
        return optimalSize
    }


    private fun getPreviewSize(cameraSize: Camera.Size?): Size {
        var width = 640
        var height = 480
        cameraSize?.let {
            width = it.width
            height = it.height
        }
        val ratio: Float = height.toFloat() / width
        return if (binding.cameraPreview.width / binding.cameraPreview.height < ratio) {
            Size((binding.cameraPreview.height * ratio).roundToInt(), binding.cameraPreview.height)
        } else {
            Size(binding.cameraPreview.width, (binding.cameraPreview.width / ratio).roundToInt())
        }
    }

}