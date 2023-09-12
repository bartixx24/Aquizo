package flashcards.genalion.aquizo.fragments

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import flashcards.genalion.aquizo.FlashcardsApplication
import flashcards.genalion.aquizo.R
import flashcards.genalion.aquizo.adapter.CollectionAdapter
import flashcards.genalion.aquizo.data.AppDataStore
import flashcards.genalion.aquizo.databinding.FragmentSetsBinding
import flashcards.genalion.aquizo.room.FlashcardsSet
import flashcards.genalion.aquizo.viewmodel.FlashcardsViewModel
import flashcards.genalion.aquizo.viewmodel.FlashcardsViewModelFactory
import kotlinx.coroutines.launch

enum class SetsOptions { FLASHCARDS, LEARN, EDIT_SET, DELETE_SET }

private const val TAG = "SetsFragment"

class SetsFragment : Fragment() {

    private var _binding: FragmentSetsBinding? = null
    private val binding get() = _binding!!

    val viewModel: FlashcardsViewModel by activityViewModels {
        FlashcardsViewModelFactory((requireActivity().application as FlashcardsApplication).database.flashcardsDao(),
            requireActivity().application)
    }

    private lateinit var appDataStore: AppDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentSetsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.resetCurrentSet()

        binding.addSetButton.setOnClickListener {
            val action = SetsFragmentDirections.actionSetsFragmentToAddSetFragment(AddSetFragment.ADD_SET)
            findNavController().navigate(action)
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(this.context)

        val itemDecoration = DividerItemDecoration(binding.recyclerView.context, DividerItemDecoration.VERTICAL)
        binding.recyclerView.addItemDecoration(itemDecoration)

        val adapter = CollectionAdapter(requireContext()) { flashcardsSet, option ->

            when(option) {
                SetsOptions.LEARN -> { learn(flashcardsSet) }
                SetsOptions.FLASHCARDS -> { showFlashcards(flashcardsSet) }
                SetsOptions.EDIT_SET -> { editSet(flashcardsSet) }
                SetsOptions.DELETE_SET -> { askIfSureToDelete(flashcardsSet) }
            }

        }

        binding.recyclerView.adapter = adapter

        val sortOptions = resources.getStringArray(R.array.sort_options)

        val sortAdapter = ArrayAdapter(requireContext(), R.layout.sort_options_item, sortOptions)
        binding.spinnerOrderOption.adapter = sortAdapter

        binding.spinnerOrderOption.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {

            override fun onItemSelected(p0: AdapterView<*>?, view: View?, position: Int, id: Long) {
                viewModel.setSortSetOption(position.toString())
                viewModel.sortFlashcardsSet()
                lifecycleScope.launch { appDataStore.saveSortOptionToPreferencesDataStore(position.toString(), requireContext()) }
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {  }

        }


        appDataStore = AppDataStore(requireContext())
        appDataStore.sortOptionPreferencesFlow.asLiveData().observe(viewLifecycleOwner) { sortOptionStringPosition ->
            Log.d(TAG, "sortOptionPosition: $sortOptionStringPosition")
            viewModel.setSortSetOption(sortOptionStringPosition)
            viewModel.sortFlashcardsSet()
        }

        Log.d(TAG, "After we got options")

        viewModel.sortSetOption.observe(viewLifecycleOwner) { option ->

            val position = if(option in 0..3) option else 1
            binding.spinnerOrderOption.setSelection(position)

        }

        viewModel.flashcardsSets.observe(viewLifecycleOwner) {
            viewModel.sortFlashcardsSet()
        }

        viewModel.flashcardsSetsSorted.observe(viewLifecycleOwner) {sortedSets ->
            sortedSets.let {
                adapter.submitList(sortedSets)
                // prevent adapter from stumbling / stuttering
                adapter.notifyDataSetChanged()
            }
        }

    }

    private fun showFlashcards(flashcardsSet: FlashcardsSet) {
        viewModel.setCurrentSet(flashcardsSet)
        val action = SetsFragmentDirections.actionSetsFragmentToFlashcardsFragment()
        findNavController().navigate(action)
    }

    private fun learn(flashcardsSet: FlashcardsSet) {

        viewModel.setCurrentSet(flashcardsSet)

        val action = SetsFragmentDirections.actionSetsFragmentToChooseLearnOptionFragment()
        findNavController().navigate(action)

    }

    private fun askIfSureToDelete(flashcardsSet: FlashcardsSet) {
        val alertDialog = AlertDialog.Builder(requireContext())
            .setTitle(resources.getString(R.string.delete_set_title))
            .setMessage(resources.getString(R.string.delete_set_message))
            .setPositiveButton(resources.getString(R.string.delete_string)) { dialog, _ ->
                deleteSet(flashcardsSet)
                dialog.dismiss()
                Toast.makeText(requireContext(), resources.getString(R.string.set_deleted_toast), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(resources.getString(R.string.cancel)) { _, _ -> }

        alertDialog.create().show()
    }

    private fun deleteSet(flashcardsSet: FlashcardsSet) {
        viewModel.deleteSet(flashcardsSet)
        viewModel.getFlashcardsToDelete(flashcardsSet.id).observe(viewLifecycleOwner) {flashcards ->
            for(flashcard in flashcards) {
                viewModel.deleteFlashcard(flashcard, true)
            }
        }
    }

    private fun editSet(flashcardsSet: FlashcardsSet) {

        viewModel.setEditSet(flashcardsSet)
        val action = SetsFragmentDirections.actionSetsFragmentToAddSetFragment(AddSetFragment.EDIT_SET)
        findNavController().navigate(action)

    }

    /** Options Menu */
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.app_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.track_progress_item -> { trackProgress() }
            R.id.rate_us_item -> { rateUs() }
            R.id.contact_item -> { contact() }
            R.id.about_us_item -> { aboutUs() }
            R.id.privacy_policy_item -> { privacyPolicy() }
            R.id.exit_item -> { exitApp() }
        }

        return super.onOptionsItemSelected(item)
    }

    private fun trackProgress() {
        val action = SetsFragmentDirections.actionSetsFragmentToTrackProgressFragment()
        findNavController().navigate(action)
    }

    private fun rateUs() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${requireActivity().packageName}"))
            startActivity(intent)
        } catch(e: ActivityNotFoundException) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${requireActivity().packageName}"))
            startActivity(intent)
        }
    }

    private fun contact() {
        val intent = Intent(Intent.ACTION_SENDTO)
            .setData(Uri.parse("mailto:" + resources.getString(R.string.email)))

        startActivity(intent)
    }

    private fun aboutUs() {

        val aboutUsView = LayoutInflater.from(requireContext()).inflate(R.layout.about_us_dialog, null)

        val aboutUsDialog = AlertDialog.Builder(requireContext())
            .setView(aboutUsView)
            .create()

        aboutUsDialog.window!!.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        aboutUsDialog.show()

    }

    private fun privacyPolicy() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://sites.google.com/view/aquizoapp"))
        //val intent = Intent(Intent.ACTION_VIEW)
        //intent.setDataAndType(Uri.parse("https://sites.google.com/view/aquizoapp"), "text/html ")
        //intent.setDataAndType(Uri.parse("https://www.freeprivacypolicy.com/live/1563b2d0-83a7-44e5-85d1-34c6bf23222c"), "text/html")
        startActivity(intent)
    }

    private fun exitApp() {
        requireActivity().finishAffinity()
    }

    override fun onDestroy() {
        _binding = null
        super.onDestroy()
    }

}