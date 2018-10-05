package tk.hacker1024.epimetheus.fragments

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.NavigationUI
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_login.*
import kotlinx.android.synthetic.main.fragment_login.view.*
import kotlinx.android.synthetic.main.nav_header.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import tk.hacker1024.epimetheus.MainActivity
import tk.hacker1024.epimetheus.R
import tk.hacker1024.epimetheus.dialogs.showLocationErrorDialog
import tk.hacker1024.epimetheus.dialogs.showNetworkErrorDialog
import tk.hacker1024.libepimetheus.InvalidLoginException
import tk.hacker1024.libepimetheus.LocationException
import tk.hacker1024.libepimetheus.PandoraException
import tk.hacker1024.libepimetheus.User
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

private const val AUTH_SHARED_PREFS_NAME = "user"
private const val EMAIL_KEY = "email"
private const val PASSWORD_KEY = "password"

//TODO (1) build settings page; add logout option

class LoginFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        activity!!.getSharedPreferences(AUTH_SHARED_PREFS_NAME, Context.MODE_PRIVATE).apply {
            if (contains(PASSWORD_KEY) && contains(EMAIL_KEY)) {
                authenticate(
                    getString(EMAIL_KEY, null)!!,
                    getString(PASSWORD_KEY, null)!!,
                    null
                )
            }

            if (contains(EMAIL_KEY)) {
                view.email.setText(getString(EMAIL_KEY, null))
            }
            if (contains(PASSWORD_KEY)) {
                view.password.setText(getString(PASSWORD_KEY, null))
            }

            view.login.setOnClickListener {
                authenticate(
                    email.text.toString(),
                    password.text.toString(),
                    this
                )
            }

            view.register.setOnClickListener {
                findNavController().navigate(LoginFragmentDirections.actionLoginFragmentToRegisterFragment())
            }
        }
    }

    private fun authenticate(email: String, password: String, sharedPrefs: SharedPreferences? = null) {
        lateinit var user: User
        toggleLoadingScreen(true)
        GlobalScope.launch {
            try {
                user = User(email, password, true)
                sharedPrefs?.edit(true) {
                    putString(EMAIL_KEY, email)
                    if (view!!.save_password.isChecked) putString(PASSWORD_KEY, password)
                }
                activity!!.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                findNavController().apply {
                    graph.startDestination = R.id.stationListFragment
                    requireActivity().runOnUiThread {
                        navigate(
                            LoginFragmentDirections.actionLoginFragmentToStationListFragment().setUser(user)
                        )
                        requireActivity().apply {
                            this.userName.text = user.username
                            this.userEmail.text = user.email
                            Picasso
                                .get()
                                .load(user.profilePicUri)
                                .placeholder(R.drawable.ic_generic_album_art)
                                .into(this.userPicture)
                        }
                        NavigationUI.setupActionBarWithNavController(requireActivity() as AppCompatActivity, findNavController(), requireActivity().drawer_layout)
                        requireActivity().drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START)
                    }
                }
            } catch (ioException: IOException) {
                when (ioException) {
                    is SocketException, is SocketTimeoutException, is UnknownHostException -> {
                        requireActivity().runOnUiThread {
                            toggleLoadingScreen(false)
                            showNetworkErrorDialog(
                                context!!,
                                exit = (activity as MainActivity)::closeAppOnDialogPress
                            )
                        }
                        coroutineContext.cancel()
                    }
                    else -> {
                        throw ioException
                    }
                }
            } catch (pandoraException: PandoraException) {
                pandoraException.printStackTrace()
                handleLoginError(pandoraException)
                coroutineContext.cancel()
            }
        }
    }

    private fun handleLoginError(pandoraException: PandoraException) {
        when (pandoraException) {
            is InvalidLoginException -> {
                requireActivity().runOnUiThread { invalidLogin() }
            }
            is LocationException -> {
                requireActivity().runOnUiThread {
                    toggleLoadingScreen(false)
                    showLocationErrorDialog(
                        context!!,
                        exit = (requireActivity() as MainActivity)::closeAppOnDialogPress
                    )
                }
            }
            else -> (requireActivity() as MainActivity).pandoraError(pandoraException.message) {}
        }
    }

    private fun invalidLogin() {
        toggleLoadingScreen(false)
        view!!.password.text.clear()
        view!!.password.background.setTint(Color.parseColor("#550000"))
        view!!.password.hint = getString(R.string.invalid_credentials_message)
    }

    private fun toggleLoadingScreen(show: Boolean) {
        if (show) {
            requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
            view!!.email.visibility = View.GONE
            view!!.password.visibility = View.GONE
            view!!.save_password.visibility = View.GONE
            view!!.login.visibility = View.GONE
            view!!.register.visibility = View.GONE
            view!!.login_progress_bar.visibility = View.VISIBLE
            view!!.loading_text.visibility = View.VISIBLE
        } else {
            view!!.login_progress_bar.visibility = View.GONE
            view!!.loading_text.visibility = View.GONE
            view!!.email.visibility = View.VISIBLE
            view!!.password.visibility = View.VISIBLE
            view!!.save_password.visibility = View.VISIBLE
            view!!.login.visibility = View.VISIBLE
            view!!.register.visibility = View.VISIBLE
            requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
    }
}
