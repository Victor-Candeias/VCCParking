package pt.vcc.parking.car

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/**
 * Ponto de entrada do Android Auto (`vp-16-android-auto`).
 *
 * E para o carro o que a `MainActivity` e para o telemovel, com uma diferenca
 * essencial: nao desenha nada. A app descreve templates e o host decide como os
 * apresenta — e isso que garante que a interface cumpre as regras de distracao
 * em qualquer veiculo, sem a app as ter de reimplementar.
 */
class VccParkingCarAppService : CarAppService() {

    /**
     * O servico tem de ser exportado para o host lhe poder ligar, o que o deixa
     * ao alcance de qualquer aplicacao instalada. E este validador, e nao uma
     * permissao do manifest, que fecha a porta.
     *
     * `ALLOW_ALL_HOSTS_VALIDATOR` fica reservado a builds `debuggable`, onde e
     * preciso para o emulador do Desktop Head Unit; em release so a lista de
     * hosts assinados pela Google e aceite.
     *
     * O `hosts_allowlist_sample` esta marcado como privado na biblioteca, mas e
     * a lista que a propria documentacao do Android for Cars manda usar e nao
     * existe equivalente publico. Reescreve-la a mao significaria manter os
     * certificados do host na app, que e exatamente o que esta lista evita.
     */
    @SuppressLint("PrivateResource")
    override fun createHostValidator(): HostValidator =
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }

    override fun onCreateSession(): Session = VccParkingSession()
}
