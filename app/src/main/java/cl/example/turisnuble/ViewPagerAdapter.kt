package cl.example.turisnuble

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class ViewPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int {
        return 3 // Tenemos 3 pestañas
    }

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> RutasCercaFragment()
            1 -> TurismoFragment()
            2 -> RutasFragment()
            else -> throw IllegalStateException("Posición de pestaña no válida: $position")
        }
    }
}