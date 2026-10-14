package com.tuapp.eleuthera.ui.spotify

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

class SpotifyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Contenedor principal que aplica el tema de la aplicación
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SpotifyScreen(vm = viewModel())
                }
            }
        }
    }
}

// Vista ejecutable y reutilizable
@Composable
fun SpotifyScreen(
    modifier: Modifier = Modifier,
    vm: SpotifyViewModel
) {
    val keyboard = LocalSoftwareKeyboardController.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp), // Margen general para toda la pantalla
        verticalArrangement = Arrangement.spacedBy(16.dp), // Espaciado limpio entre elementos
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Campo superior para la URL de Spotify (altura controlada)
        OutlinedTextField(
            value = vm.urlTexto,
            onValueChange = { vm.urlTexto = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Url de Spotify (track)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); vm.submit() })
        )

        // Textbox gigante scrolleable
        OutlinedTextField(
            value = vm.jsonResponse,
            onValueChange = {}, // Solo lectura
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            label = { Text("Salida de la API") },
            placeholder = { Text("Spotify API response here") },
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            )
        )
    }
}

fun getMockupData(): String{
    return "[197 elements\n" +
            "{5 entries\n" +
            "    name: \"Adeel Solangi\",\n" +
            "    language: \"Sindhi\",\n" +
            "    id: \"V59OF92YF627HFY0\",\n" +
            "    bio: \"Donec lobortis eleifend condimentum. Cras dictum dolor lacinia lectus vehicula rutrum. Maecenas quis nisi nunc. Nam tristique feugiat est vitae mollis26\",\n" +
            "    version: 6.1\n" +
            "},\n" +
            "{5 entries\n" +
            "    name: \"Afzal Ghaffar\",\n" +
            "    language: \"Sindhi\",\n" +
            "    id: \"ENTOCR13RSCLZ6KU\",\n" +
            "    bio: \"Aliquam sollicitudin ante ligula, eget malesuada nibh efficitur et. Pellentesque massa sem, scelerisque sit amet odio id, cursus tempor urna. Etiam co77\",\n" +
            "    version: 1.88\n" +
            "},\n" +
            "{5 entries\n" +
            "    name: \"Aamir Solangi\",\n" +
            "    language: \"Sindhi\",\n" +
            "    id: \"IAKPO3R4761JDRVG\",\n" +
            "    bio: \"Vestibulum pharetra libero et velit gravida euismod. Quisque mauris ligula, efficitur porttitor sodales ac, lacinia non ex. Fusce eu ultrices elit, ve16\",\n" +
            "    version: 7.27\n" +
            "},\n" +
            "{5 entries\n" +
            "    name: \"Abla Dilmurat\",\n" +
            "    language: \"Uyghur\",\n" +
            "    id: \"5ZVOEPMJUI4MB4EN\",\n" +
            "    bio: \"Donec lobortis eleifend condimentum. Morbi ac tellus erat.\",\n" +
            "    version: 2.53\n" +
            "},\n" +
            "{5 entries\n" +
            "    name: \"Adil Eli\",\n" +
            "    language: \"Uyghur\",\n" +
            "    id: \"6VTI8X6LL0MMPJCC\",\n" +
            "    bio: \"Vivamus id faucibus velit, id posuere leo. Morbi vitae nisi lacinia, laoreet lorem nec, egestas orci. Suspendisse potenti.\",\n" +
            "    version: 6.49\n" +
            "},\n" +
            "{5 entries\n" +
            "    name: \"Adile Qadir\",\n" +
            "    language: \"Uyghur\",\n" +
            "    id: \"F2KEU5L7EHYSYFTT\",\n" +
            "    bio: \"Duis commodo orci ut dolor iaculis facilisis. Morbi ultricies consequat ligula posuere eleifend. Aenean finibus in tortor vel aliquet. Fusce eu ultric27\",\n" +
            "    version: 1.9\n" +
            "},\n" +
            "{5 entries\n" +
            "    name: \"Abdukerim Ibrahim\",\n" +
            "    language: \"Uyghur\",\n" +
            "    id: \"LO6DVTZLRK68528I\",\n" +
            "    bio: \"Vivamus id faucibus velit, id posuere leo. Nunc aliquet sodales nunc a pulvinar. Nunc aliquet sodales nunc a pulvinar. Ut viverra quis eros eu tincidunt.\",\n" +
            "    version: 5.9\n" +
            "},\n" +
            "{5 entries\n" +
            "    name: \"Adil Abro\",\n" +
            "    language: \"Sindhi\",\n" +
            "    id: \"LJRIULRNJFCNZJAJ\",\n" +
            "    bio: \"Etiam malesuada blandit erat, nec ultricies leo maximus sed. Fusce congue aliquam elit ut luctus. Etiam malesuada blandit erat, nec ultricies leo maxi184\",\n" +
            "    version: 9.32\n" +
            "},\n" +
            "{5 entries\n" +
            "    name: \"Afonso Vilarchán\",\n" +
            "    language: \"Galician\",\n" +
            "    id: \"JMCL0CXNXHPL1GBC\",\n" +
            "    bio: \"Fusce eu ultrices elit, vel posuere neque. Morbi ac tellus erat. Nunc tincidunt laoreet laoreet.\",\n" +
            "    version: 5.21\n" +
            "},\n" +
            "{5 entries\n" +
            "    name: \"Mark Schembri\",\n" +
            "    language: \"Maltese\",\n" +
            "    id: \"KU4T500C830697CW\",\n" +
            "    bio: \"Nam laoreet, nunc non suscipit interdum, justo turpis vestibulum massa, non vulputate ex urna at purus. Morbi ultricies consequat ligula posuere eleif206\",\n" +
            "    version: 3.17\n" +
            "},\n" +
            "{5 entries\n" +
            "    name: \"Antía Sixirei\",\n" +
            "    language: \"Galician\",\n" +
            "    id: \"XOF91ZR7MHV1TXRS\",\n" +
            "    bio: \"Pellentesque massa sem, scelerisque sit amet odio id, cursus tempor urna. Phasellus massa ligula, hendrerit eget efficitur eget, tincidunt in ligula. 132\",\n" +
            "    version: 6.44\n" +
            "},\n" +
            "{5 entries\n" +
            "    name: \"Aygul Mutellip\",\n" +
            "    language: \"Uyghur\",\n" +
            "    id: \"FTSNV411G5MKLPDT\",\n" +
            "    bio: \"Duis commodo orci ut dolor iaculis facilisis. Nam semper gravida nunc, sit amet elementum ipsum. Donec pellentesque ultrices mi, non consectetur eros 85\",\n" +
            "    version: 9.1\n" +
            "},\n" +
            "{5 entries\n" +
            "    name: \"Awais Shaikh\",\n" +
            "    language: \"Sindhi\",\n" +
            "    id: \"OJMWMEEQWMLDU29P\",\n" +
            "    bio: \"Nunc aliquet sodales nunc a pulvinar. Ut dictum, ligula eget sagittis maximus, tellus mi varius ex, a accumsan justo tellus vitae leo. Donec pellentes123\",\n" +
            "    version: 1.59\n" +
            "},\n" +
            "{5 entries\n" +
            "    name: \"Ambreen Ahmed\",\n" +
            "    language: \"Sindhi\",\n" +
            "    id: \"5G646V7E6TJW8X2M\",\n" +
            "    bio: \"Vestibulum ante ipsum primis in faucibus orci luctus et ultrices posuere cubilia curae; Etiam consequat enim lorem, at tincidunt velit ultricies et. U101\",\n" +
            "    version: 2.35\n" +
            "},\n" +
            "{5 entries\n" +
            "    name: \"Celtia Anes\",\n" +
            "    language: \"Galician\",\n" +
            "    id: \"Z53AJY7WUYPLAWC9\",\n" +
            "    bio: \"Nullam ac sodales dolor, eu facilisis dui. Maecenas non arcu nulla. Ut viverra quis eros eu tincidunt. Curabitur quis commodo quam.\",\n" +
            "    version: 8.34\n" +
            "},\n" +
            "{5 entries\n" +
            "    name: \"George Mifsud\",\n" +
            "    language: \"Maltese\",\n" +
            "    id: \"N1AS6UFULO6WGTLB\",\n" +
            "    bio: \"Phasellus tincidunt sollicitudin posuere. Ut accumsan, est vel fringilla varius, purus augue blandit nisl, eu rhoncus ligula purus vel dolor. Donec co159\",\n" +
            "    version: 7.47\n" +
            "},\n" +
            "{5 entries\n" +
            "    name: \"Aytürk Qasim\",\n" +
            "    language: \"Uyghur\",\n" +
            "    id: \"70RODUVRD95CLOJL\",\n" +
            "    bio: \"Curabitur ultricies id urna nec ultrices. Aliquam scelerisque pretium tellus, sed accumsan est ultrices id. Duis commodo orci ut dolor iaculis facilisis.\",\n" +
            "    version: 1.32\n" +
            "},\n" +
            "{5 entries\n" +
            "    name: \"Dialè Meso\",\n" +
            "    language: \"Sesotho sa Leboa\",\n" +
            "    id: \"VBLI24FKF7VV6BWE\",\n" +
            "    bio: \"Maecenas non arcu nulla. Vivamus id faucibus velit, id posuere leo. Nullam sodales convallis mauris, sit amet lobortis magna auctor sit amet.\",\n" +
            "    version: 6.29\n" +
            "},\n" +
            "{5 entries\n" +
            "    name: \"Breixo Galáns\",\n" +
            "    language: \"Galician\",\n" +
            "    id: \"4VRLON0GPEZYFCVL\",\n" +
            "    bio: \"Integer vehicula, arcu sit amet egestas efficitur, orci justo interdum massa, eget ullamcorper risus ligula tristique libero. Morbi ac tellus erat. In155\",\n" +
            "    version: 1.62\n" +
            "},\n" +
            "{5 entries\n" +
            "    name: \"Bieito Lorme\",\n" +
            "    language: \"Galician\",\n" +
            "    id: \"5DRDI1QLRGLP29RC\",\n" +
            "    bio: \"Ut viverra quis eros eu tincidunt. Morbi vitae nisi lacinia, laoreet lorem nec, egestas orci. Curabitur quis commodo quam. Morbi ac tellus erat.\",\n" +
            "    version: 4.45\n" +
            "},\n" +
            "{5 entries\n" +
            "    name: \"Azrugul Osman\",\n" +
            "    language: \"Uyghur\",\n" +
            "    id: \"5RCTVD3C5QGVAKTQ\",\n" +
            "    bio: \"Maecenas tempus neque ut porttitor malesuada. Donec lobortis eleifend condimentum.\",\n" +
            "    version: 3.18\n" +
            "},\n" +
            "{5 entries\n" +
            "    name: \"Brais Verdiñas\",\n" +
            "    language: \"Galician\",\n" +
            "    id: \"BT407GHCC0IHXCD3\",\n" +
            "    bio: \"Quisque maximus sodales mauris ut elementum. Ut viverra quis eros eu tincidunt. Sed eu libero maximus nunc lacinia lobortis et sit amet nisi. In id el75\",\n" +
            "    version: 5.01\n" +
            "},\n" +
            "{5 entries\n" +
            "    name: \"Ekber Sadir\",\n" +
            "    language: \"Uyghur\",\n" +
            "    id: \"AGZDAP8D8OVRRLTY\",\n" +
            "    bio: \"Quisque efficitur vel sapien ut imperdiet. Phasellus massa ligula, hendrerit eget efficitur eget, tincidunt in ligula. In id elit malesuada, pulvinar 174\",\n" +
            "    version: 2.04\n" +
            "},\n" +
            "{5 entries\n" +
            "    name: \"Doreen Bartolo\",\n" +
            "    language: \"Maltese\",\n" +
            "    id: \"59QSX02O2XOZGRLH\",\n" +
            "    bio: \"Vestibulum ante ipsum primis in faucibus orci luctus et ultrices posuere cubilia curae; Etiam consequat enim lorem, at tincidunt velit ultricies et. N133\",\n" +
            "    version: 9.31\n" +
            "},\n" +
            "{5 entries\n" +
            "    name: \"Ali Ayaz\",\n" +
            "    language: \"Sindhi\",\n" +
            "    id: \"3WNLUZ5LT2F7MYVU\",\n" +
            "    bio: \"Cras dictum dolor lacinia lectus vehicula rutrum. Vestibulum ante ipsum primis in faucibus orci luctus et ultrices posuere cubilia curae; Etiam conseq109\",\n" +
            "    version: 7.8\n" +
            "},\n" +
            "{5 entries\n" +
            "    name: \"Guzelnur Polat\",\n" +
            "    language: \"Uyghur\",\n" +
            "    id: \"I6QQHAEGV4CYDXLP\",\n" +
            "    bio: \"Nam laoreet, nunc non suscipit interdum, justo turpis vestibulum massa, non vulputate ex urna at purus. Vestibulum ante ipsum primis in faucibus orci 144\",\n" +
            "    version: 8.56\n" +
            "},\n" +
            "{5 entries\n" +
            "    name: \"John Falzon\",\n" +
            "    language: \"Maltese\",\n" +
            "    id: \"U3AWXHDTSU0H82SL\",\n" +
            "    bio: \"Sed nec suscipit ligula. Nullam sodales convallis mauris, sit amet lobortis magna auctor sit amet.\",\n" +
            "    version: 9.96\n" +
            "},\n" +
            "{5 entries\n" +
            "    name: \"Erkin Qadir\",\n" +
            "    language: \"Uyghur\",\n" +
            "    id: \"GV6TA1AATZYBJ3VR\",\n" +
            "    bio: \"Phasellus massa ligula, hendrerit eget efficitur eget, tincidunt in ligula. .\",\n" +
            "    version: 3.53\n" +
            "},\n" +
            "{5 entries\n" +
            "    name: \"Anita Rajput\",\n" +
            "    language: \"Sindhi\",\n" +
            "    id: \"XLLVD0NO2ZFEP4AK\",\n" +
            "    bio: \"Nam semper gravida nunc, sit amet elementum ipsum. Etiam congue dignissim volutpat.\",\n" +
            "    version: 5.16\n" +
            "},\n" +
            "{5 entries\n" +
            "    name: \"Ayesha Khalique\",\n" +
            "    language: \"Sindhi\",\n" +
            "    id: \"Q9A5QNGA0OSU8P6Y\",\n" +
            "    bio: \"Morbi vitae nisi lacinia, laoreet lorem nec, egestas orci. Etiam mauris magna, fermentum vitae aliquet eu, cursus vitae sapien.\",\n" +
            "    version: 3.9\n" +
            "},\n" +
            "{5 entries\n" +
            "    name: \"Pheladi Rammala\",\n" +
            "    language: \"Sesotho sa Leboa\",\n" +
            "    id: \"EELSIRT2T4Q0M3M4\",\n" +
            "    bio: \"Quisque efficitur vel sapien ut imperdiet. Morbi ac tellus erat. Aliquam scelerisque pretium tellus, sed accumsan est ultrices id. Ut maximus, libero 83\",\n" +
            "    version: 1.88\n" +
            "},\n" +
            "{5 entries\n" +
            "    name: \"Antón Caneiro\",\n" +
            "    language: \"Galician\",\n" +
            "    id: \"ENTAPNU3MMFUGM1W\",\n" +
            "    bio: \"Integer vehicula, arcu sit amet egestas efficitur, orci justo interdum massa, eget ullamcorper risus ligula tristique libero. Vestibulum pharetra libe28\",\n" +
            "    version: 4.84\n" +
            "},\n" +
            "{5 entries\n" +
            "    name: \"Qahar Abdulla\",\n" +
            "    language: \"Uyghur\",\n" +
            "    id: \"OGLODUPEHKEW0K83\",\n" +
            "    bio: \"Duis commodo orci ut dolor iaculis facilisis. Aliquam sollicitudin ante ligula, eget malesuada nibh efficitur et. Fusce congue aliquam elit ut luctus.171\",\n" +
            "    version: 3.65\n" +
            "},\n" +
            "{5 entries\n" +
            "    name: \"Reyhan Murat\",\n" +
            "    language: \"Uyghur\",\n" +
            "    id: \"Y91F4D54794E9ANT\",\n" +
            "    bio: \"Suspendisse sit amet ullamcorper sem. Curabitur sed condimentum felis, ut luctus eros.\",\n" +
            "    version: 2.69\n" +
            "},\n" +
            "{5 entries\n" +
            "    name: \"Tatapi Phogole\",\n" +
            "    language: \"Sesotho sa Leboa\",\n" +
            "    id: \"7JA42P5CMCWDVPNR\",\n" +
            "    bio: \"Duis luctus, lacus eu aliquet convallis, purus elit malesuada ex, vitae rutrum ipsum dui ut magna. Nullam ac sodales dolor, eu facilisis dui. Ut viver26\",\n" +
            "    version: 3.78\n" +
            "},\n" +
            "{5 entries\n" +
            "    name: \"Marcos Amboade\",\n" +
            "    language: \"Galician\",\n" +
            "    id: \"WPX7H97C7D70CZJR\",\n" +
            "    bio: \"Nulla finibus massa at viverra facilisis. Pellentesque massa sem, scelerisque sit amet odio id, cursus tempor urna. Curabitur ultricies id urna nec ul148\",\n" +
            "    version: 7.37\n" +
            "},\n" +
            "{5 entries\n" +
            "    name: \"Grace Tabone\",\n" +
            "    language: \"Maltese\",\n" +
            "    id: \"K4XO8G8DMRNSHF2B\",\n" +
            "    bio: \"Curabitur sed condimentum felis, ut luctus eros. Duis luctus, lacus eu aliquet convallis, purus elit malesuada ex, vitae rutrum ipsum dui ut magna.\",\n" +
            "    version: 5.36\n" +
            "},\n" +
            "{5 entries\n" +
            "    name: \"Shafqat Memon\",\n" +
            "    language: \"Sindhi\",\n" +
            "    id: \"D8VFLVRXBXMVBRVI\",\n" +
            "    bio: \"Aliquam scelerisque pretium tellus, sed accumsan est ultrices id. . Curabitur quis commodo quam. Quisque maximus sodales mauris ut elementum. Quisque 62\",\n" +
            "    version: 8.95\n" +
            "},\n" +
            "{5 entries\n" +
            "    name: \"Zeynep Semet\",\n" +
            "    language: \"Uyghur\",\n" +
            "    id: \"Z324TZV8S0FGDSAO\",\n" +
            "    bio: \"Quisque mauris ligula, efficitur porttitor sodales ac, lacinia non ex. Fusce eu ultrices elit, vel posuere neque. Nulla finibus massa at viverra facilisis.\",\n" +
            "    version: 1.03\n" +
            "},\n" +
            "{5 entries\n" +
            "    name: \"Meladi Papo\",\n" +
            "    language: \"Sesotho sa Leboa\",\n" +
            "    id: \"RJAZQ6BBLRT72CD9\",\n" +
            "    bio: \"Quisque efficitur vel sapien ut imperdiet. Pellentesque massa sem, scelerisque sit amet odio id, cursus tempor urna. Ut accumsan, est vel fringilla va141\",\n" +
            "    version: 7.22\n" +
            "},\n" +
            "{5 entries\n" +
            "    name: \"Semet Alim\",\n" +
            "    language: \"Uyghur\",\n" +
            "    id: \"HI7L2SR4RCS8C8CS\",\n" +
            "    bio: \"Duis commodo orci ut dolor iaculis facilisis. Ut viverra quis eros eu tincidunt. Lorem ipsum dolor sit amet, consectetur adipiscing elit.\",\n" +
            "    version: 1.01\n" +
            "},\n" +
            "{5 entries\n" +
            "    name: \"Sabela Veloso\",\n" +
            "    language: \"Galician\",\n" +
            "    id: \"QA55WXDLC7SRH97X\",\n" +
            "    bio: \"Duis commodo orci ut dolor iaculis facilisis. Suspendisse potenti. Cras dictum dolor lacinia lectus vehicula rutrum.\",\n" +
            "    version: 7.32\n" +
            "},\n" +
            "{5 entries\n" +
            "    name: \"Madule Ledimo\",\n" +
            "    language: \"Sesotho sa Leboa\",\n" +
            "    id: \"IHJN2DGJB5O1Y00D\",\n" +
            "    bio: \"Maecenas non arcu nulla. Aliquam scelerisque pretium tellus, sed accumsan est ultrices id.\",\n" +
            "    version: 7.47\n" +
            "},\n" +
            "{5 entries\n" +
            "    name: \"Michelle Caruana\",\n" +
            "    language: \"Maltese\",\n" +
            "    id: \"EG1I21R75IV9Q0Q8\",\n" +
            "    bio: \"Nam tristique feugiat est vitae mollis. Morbi ultricies consequat ligula posuere eleifend. Pellentesque massa sem, scelerisque sit amet odio id, cursu14\",\n" +
            "    version: 4.95\n" +
            "},\n" +
            "{5 entries\n" +
            "    name: \"Philip Camilleri\",\n" +
            "    language: \"Maltese\",\n" +
            "    id: \"FCO0URUHARX5FDFW\",\n" +
            "    bio: \"Quisque efficitur vel sapien ut imperdiet. Suspendisse sit amet ullamcorper sem. Aliquam scelerisque pretium tellus, sed accumsan est ultrices id. . A36\",\n" +
            "    version: 9.97\n" +
            "}]"
}