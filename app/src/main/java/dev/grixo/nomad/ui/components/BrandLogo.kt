package dev.grixo.nomad.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.grixo.nomad.R

@Composable
fun BrandLogo(
    modifier: Modifier = Modifier
        .height(40.dp)
        .widthIn(max = 180.dp)
) {
    Image(
        painter = painterResource(R.drawable.logo_nomad),
        contentDescription = "Nomad",
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}
